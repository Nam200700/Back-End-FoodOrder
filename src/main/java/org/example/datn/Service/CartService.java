package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.cart.AddCartItemRequest;
import org.example.datn.DTO.response.cart.CartResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.CartRepository;
import org.example.datn.Repository.FoodRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.domain.Cart;
import org.example.datn.domain.CartItem;
import org.example.datn.domain.Food;
import org.example.datn.domain.Restaurant;
import org.example.datn.mapper.CartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final FoodRepository foodRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public List<CartResponse> getCart(Long customerId) {
        return cartRepository.findByCustomerUserId(customerId).stream()
                .map(this::buildResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public CartResponse addItem(Long customerId, AddCartItemRequest req) {
        Food food = foodRepository.findByIdOrThrow(req.getFoodId(), ErrorCode.FOOD_NOT_FOUND);
        Restaurant newRestaurant = food.getRestaurant();

        Optional<Cart> existing = cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, newRestaurant.getRestaurantId());
        Cart cart;
        if (existing.isPresent()) {
            cart = existing.get();
            addOrUpdateItem(cart, food, req);
        } else {
            if (req.getQuantity() <= 0) {
                return CartResponse.builder()
                        .restaurantId(newRestaurant.getRestaurantId())
                        .restaurantName(newRestaurant.getRestaurantName())
                        .items(List.of())
                        .subtotal(BigDecimal.ZERO)
                        .latitude(newRestaurant.getLatitude())
                        .longitude(newRestaurant.getLongitude())
                        .build();
            }
            cart = Cart.builder()
                    .customer(userRepository.getReferenceById(customerId))
                    .restaurant(newRestaurant)
                    .build();
            cartRepository.save(cart);
            addOrUpdateItem(cart, food, req);
        }

        if (cart.getItems().isEmpty()) {
            return CartResponse.builder()
                    .restaurantId(newRestaurant.getRestaurantId())
                    .restaurantName(newRestaurant.getRestaurantName())
                    .items(List.of())
                    .subtotal(BigDecimal.ZERO)
                    .latitude(newRestaurant.getLatitude())
                    .longitude(newRestaurant.getLongitude())
                    .build();
        }
        return buildResponse(cart);
    }

    /** Used by FE after the customer confirms replacing a conflicting cart. */
    @Transactional
    public CartResponse replaceCart(Long customerId, AddCartItemRequest req) {
        return addItem(customerId, req);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long cartItemId) {
        List<Cart> carts = cartRepository.findByCustomerUserId(customerId);
        Cart targetCart = null;
        for (Cart c : carts) {
            boolean hasItem = c.getItems().stream().anyMatch(i -> i.getCartItemId().equals(cartItemId));
            if (hasItem) {
                targetCart = c;
                break;
            }
        }

        if (targetCart == null) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        targetCart.getItems().removeIf(i -> i.getCartItemId().equals(cartItemId));
        if (targetCart.getItems().isEmpty()) {
            cartRepository.delete(targetCart);
            return CartResponse.builder()
                    .restaurantId(targetCart.getRestaurant().getRestaurantId())
                    .restaurantName(targetCart.getRestaurant().getRestaurantName())
                    .items(List.of())
                    .subtotal(BigDecimal.ZERO)
                    .latitude(targetCart.getRestaurant().getLatitude())
                    .longitude(targetCart.getRestaurant().getLongitude())
                    .build();
        }
        cartRepository.save(targetCart);
        return buildResponse(targetCart);
    }

    @Transactional
    public void clearCart(Long customerId, Long restaurantId) {
        if (restaurantId != null) {
            cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurantId)
                    .ifPresent(cartRepository::delete);
        } else {
            List<Cart> carts = cartRepository.findByCustomerUserId(customerId);
            cartRepository.deleteAll(carts);
        }
    }

    private void addOrUpdateItem(Cart cart, Food food, AddCartItemRequest req) {
        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getFood().getFoodId().equals(food.getFoodId()))
                .findFirst();
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + req.getQuantity();
            if (newQty <= 0) {
                cart.getItems().remove(item);
            } else {
                item.setQuantity(newQty);
                if (req.getNote() != null) {
                    item.setNote(req.getNote());
                }
            }
        } else if (req.getQuantity() > 0) {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .food(food)
                    .quantity(req.getQuantity())
                    .note(req.getNote())
                    .build());
        }

        if (cart.getItems().isEmpty()) {
            cartRepository.delete(cart);
        } else {
            cartRepository.save(cart);
        }
    }

    private CartResponse buildResponse(Cart cart) {
        CartResponse response = cartMapper.toResponse(cart);
        BigDecimal subtotal = cart.getItems().stream()
                .map(i -> i.getFood().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        response.setSubtotal(subtotal);
        return response;
    }
}

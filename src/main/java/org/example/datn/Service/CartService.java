package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.cart.AddCartItemRequest;
import org.example.datn.DTO.response.cart.CartResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.*;
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
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public List<CartResponse> getCart(Long customerId) {
        return cartRepository.findByCustomerUserId(customerId).stream()
                .map(cartMapper::toResponse)
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
                return cartMapper.toEmptyResponse(newRestaurant);
            }
            cart = Cart.builder()
                    .customer(userRepository.getReferenceById(customerId))
                    .restaurant(newRestaurant)
                    .build();
            addOrUpdateItem(cart, food, req);
        }

        if (cart.getItems().isEmpty()) {
            cartRepository.delete(cart);
            return cartMapper.toEmptyResponse(newRestaurant);
        }
        cartRepository.save(cart);
        return cartMapper.toResponse(cart);
    }

    /** Used by FE after the customer confirms replacing a conflicting cart. */
    @Transactional
    public CartResponse replaceCart(Long customerId, AddCartItemRequest req) {
        return addItem(customerId, req);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long cartItemId) {
        CartItem item = cartItemRepository.findByIdOrThrow(cartItemId, ErrorCode.CART_ITEM_NOT_FOUND);
        Cart cart = item.getCart();
        if (!cart.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        cart.getItems().remove(item);
        if (cart.getItems().isEmpty()) {
            Restaurant restaurant = cart.getRestaurant();
            cartRepository.delete(cart);
            return cartMapper.toEmptyResponse(restaurant);
        }
        cartRepository.save(cart);
        return cartMapper.toResponse(cart);
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

//        if (cart.getItems().isEmpty()) {
//            cartRepository.delete(cart);
//        } else {
//            cartRepository.save(cart);
//        }
    }

//    private CartResponse buildResponse(Cart cart) {
//        CartResponse response = cartMapper.toResponse(cart);
//        BigDecimal subtotal = cart.getItems().stream()
//                .map(i -> i.getFood().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//        response.setSubtotal(subtotal);
//        return response;
//    }
}

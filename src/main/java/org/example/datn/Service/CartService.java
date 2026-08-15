package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.cart.AddCartItemRequest;
import org.example.datn.DTO.request.cart.UpdateCartItemNoteRequest;
import org.example.datn.DTO.request.cart.UpdateCartItemQuantityRequest;
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
        return cartRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId).stream()
                .map(cartMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public CartResponse addItem(Long customerId, AddCartItemRequest req) {
        Food food = foodRepository.findByIdOrThrow(
                req.getFoodId(),
                ErrorCode.FOOD_NOT_FOUND
        );
        Restaurant restaurant = food.getRestaurant();
        Cart cart = cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurant.getRestaurantId())
                .orElseGet(() -> Cart.builder()
                        .customer(userRepository.getReferenceById(customerId))
                        .restaurant(restaurant)
                        .build());

        boolean exists = cart.getItems().stream()
                .anyMatch(item -> item.getFood().getFoodId().equals(food.getFoodId()));

        if (exists) {
            throw new AppException(ErrorCode.CART_ITEM_ALREADY_EXISTS);
        }

        cart.getItems().add(
                CartItem.builder()
                        .cart(cart)
                        .food(food)
                        .quantity(req.getQuantity())
                        .note(req.getNote() != null ? req.getNote() : "")
                        .build()
        );

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
            List<Cart> carts = cartRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId);
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
                    .note(req.getNote() != null ? req.getNote() : "")
                    .build());
        }
    }

    @Transactional
    public CartResponse updateItemNote(Long customerId, UpdateCartItemNoteRequest req) {
        Cart cart = cartRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId).stream()
                .filter(c -> c.getItems().stream().anyMatch(i -> i.getFood().getFoodId().equals(req.getFoodId())))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getFood().getFoodId().equals(req.getFoodId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        item.setNote(req.getNote());

        cartRepository.save(cart);
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(
            Long customerId,
            Long cartItemId,
            UpdateCartItemQuantityRequest req
    ) {
        CartItem item = cartItemRepository.findByIdOrThrow(
                cartItemId,
                ErrorCode.CART_ITEM_NOT_FOUND
        );

        Cart cart = item.getCart();

        if (!cart.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // quantity = 0 → xóa món
        if (req.getQuantity() == 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);

            if (cart.getItems().isEmpty()) {
                Restaurant restaurant = cart.getRestaurant();
                cartRepository.delete(cart);
                return cartMapper.toEmptyResponse(restaurant);
            }

            cartRepository.save(cart);
            return cartMapper.toResponse(cart);
        }

        // quantity > 0 → cập nhật số lượng
        item.setQuantity(req.getQuantity());

        cartRepository.save(cart);

        return cartMapper.toResponse(cart);
    }


}

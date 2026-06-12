package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.cart.AddCartItemRequest;
import org.example.datn.DTO.response.cart.CartResponse;
import org.example.datn.Service.CartService;
import org.example.datn.common.ApiResponse;
import org.example.datn.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts/me")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<CartResponse>>> getCart(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(user.getUserId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AddCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(user.getUserId(), req)));
    }

    /** Called after the FE confirms replacing a conflicting (other-restaurant) cart. */
    @PostMapping("/items/replace")
    public ResponseEntity<ApiResponse<CartResponse>> replaceCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AddCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.replaceCart(user.getUserId(), req)));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long cartItemId) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(user.getUserId(), cartItemId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Long restaurantId) {
        cartService.clearCart(user.getUserId(), restaurantId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa giỏ hàng"));
    }
}

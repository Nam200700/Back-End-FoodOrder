package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.RejectOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Service.OrderService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/orders")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long restaurantId,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                orderService.getMerchantOrders(user.getUserId(), restaurantId, status, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOne(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getMerchantOrder(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirm(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.confirmOrder(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<OrderResponse>> reject(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody RejectOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.rejectOrder(user.getUserId(), id, req.getCancelReason())));
    }

    @PatchMapping("/{id}/preparing")
    public ResponseEntity<ApiResponse<OrderResponse>> preparing(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.markPreparing(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/ready")
    public ResponseEntity<ApiResponse<OrderResponse>> ready(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.markReadyForPickup(user.getUserId(), id)));
    }
}

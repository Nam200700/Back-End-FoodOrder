package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.OrderService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipper/orders")
@PreAuthorize("hasRole('SHIPPER')")
@RequiredArgsConstructor
public class ShipperOrderController {

    private final OrderService orderService;

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> available() {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getAvailableOrders()));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<OrderResponse>> accept(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.acceptOrder(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/abandon")
    public ResponseEntity<ApiResponse<OrderResponse>> abandon(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody org.example.datn.DTO.request.order.CancelOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.abandonOrder(user.getUserId(), id, req.getReason())));
    }

    @PatchMapping("/{id}/picked-up")
    public ResponseEntity<ApiResponse<OrderResponse>> pickedUp(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.markPickedUp(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/delivering")
    public ResponseEntity<ApiResponse<OrderResponse>> delivering(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.markDelivering(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> complete(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.markCompleted(user.getUserId(), id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                orderService.getShipperOrders(user.getUserId(), status, pageable))));
    }
}

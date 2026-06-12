package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Service.OrderService;
import org.example.datn.common.ApiResponse;
import org.example.datn.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody CancelOrderRequest req,
            @AuthenticationPrincipal CustomUserDetails principal) {

        OrderResponse res = orderService.cancelOrder(orderId, req, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}

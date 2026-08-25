package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.CreateOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Service.OrderService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(orderService.createOrder(user.getUserId(), req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> myOrders(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        // toDate lấy tới hết ngày (đầu ngày hôm sau) để bao trọn ngày được chọn
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(orderService.getCustomerOrders(user.getUserId(), status, keyword, from, to, pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOne(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getCustomerOrder(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.cancelOrderByCustomer(user.getUserId(), id, req.getReason())));
    }
}

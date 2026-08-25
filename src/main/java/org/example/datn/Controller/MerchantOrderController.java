package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.RejectOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.DTO.response.order.MerchantOrderMonitorResponse;
import org.example.datn.Service.OrderService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        // toDate lấy tới hết ngày (đầu ngày hôm sau) để bao trọn ngày được chọn
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                orderService.getMerchantOrders(user.getUserId(), restaurantId, status, keyword, from, to, pageable))));
    }
    /** Theo dõi nhẹ: đếm đơn theo trạng thái + danh sách đơn chờ rút gọn (thay việc tải cả nghìn đơn để đếm). */
    @GetMapping("/monitor")
    public ResponseEntity<ApiResponse<MerchantOrderMonitorResponse>> monitor(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getMerchantOrderMonitor(user.getUserId(), restaurantId)));
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
                orderService.rejectOrder(user.getUserId(), id, req.getRejectReason())));
    }

    /** Quán HỦY đơn SAU khi đã xác nhận (CONFIRMED/PREPARING) — hết nguyên liệu/quá tải. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.cancelOrder(id, req, user.getUserId())));
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
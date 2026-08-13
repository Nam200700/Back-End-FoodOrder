package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.grouporder.*;
import org.example.datn.DTO.response.grouporder.GroupOrderResponse;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Service.GroupOrderService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/group-orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class GroupOrderController {

    private final GroupOrderService groupOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupOrderResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateGroupOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(groupOrderService.createGroupOrder(user.getUserId(), req)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<GroupOrderResponse>>> myGroupOrders(
            @AuthenticationPrincipal CustomUserDetails user, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(groupOrderService.getMyGroupOrders(user.getUserId(), pageable))));
    }

    /** Khôi phục phiên đang hoạt động (OPEN/LOCKED) của user tại 1 quán. */
    @GetMapping("/active/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> getActiveForRestaurant(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long restaurantId) {
        GroupOrderResponse res = groupOrderService.getActiveGroupOrderForRestaurant(user.getUserId(), restaurantId);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.getDetail(user.getUserId(), id)));
    }

    @GetMapping("/invite/{inviteCode}")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> preview(@PathVariable String inviteCode) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.getByInviteCode(inviteCode)));
    }

    @PostMapping("/invite/{inviteCode}/join")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> join(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable String inviteCode) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.joinGroupOrder(user.getUserId(), inviteCode)));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        groupOrderService.leaveGroupOrder(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> addItem(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody AddGroupOrderItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.addItem(user.getUserId(), id, req)));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> updateItem(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody UpdateGroupOrderItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.updateItem(user.getUserId(), id, itemId, req)));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> removeItem(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id, @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.removeItem(user.getUserId(), id, itemId)));
    }

    @PatchMapping("/{id}/ready")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> markReady(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.markReady(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<GroupOrderResponse>> lock(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(groupOrderService.lockGroupOrder(user.getUserId(), id)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody CancelGroupOrderRequest req) {
        groupOrderService.cancelGroupOrder(user.getUserId(), id, req.getReason());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody CheckoutGroupOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(groupOrderService.checkout(user.getUserId(), id, req)));
    }
}
package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.DTO.request.report.ResolveReportRequest;
import org.example.datn.DTO.request.auth.ReviewRegisterRequest;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.DTO.response.restaurant.RestaurantRegisterResponse;
import org.example.datn.DTO.response.auth.ShipperRegisterResponse;
import org.example.datn.DTO.response.report.ReportResponse;
import org.example.datn.DTO.response.stats.StatsOverviewResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.AdminService;
import org.example.datn.Service.ReportService;
import org.example.datn.Service.StatisticsService;
import org.example.datn.Service.OrderService;
import org.example.datn.DTO.response.order.OrderResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;
    private final StatisticsService statisticsService;
    private final OrderService orderService;

    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<StatsOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.adminOverview()));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> orders(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(orderService.getAllOrders(pageable))));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> users(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(pageable)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> setStatus(
            @PathVariable Long id,
            @RequestParam boolean active,
            @RequestParam(required = false) String lockedReason) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.setUserStatus(id, active, lockedReason)));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> reports(
            @RequestParam(required = false) ReportStatus status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.list(status, pageable)));
    }

    @PatchMapping("/reports/{id}/resolve")
    public ResponseEntity<ApiResponse<ReportResponse>> resolve(
            @PathVariable Long id, @Valid @RequestBody ResolveReportRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.resolve(id, req.getStatus())));
    }

    @GetMapping("/restaurant-registers")
    public ResponseEntity<ApiResponse<PageResponse<RestaurantRegisterResponse>>> listRestaurantRegisters(
            @RequestParam(required = false) RegisterStatus status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listRestaurantRegisters(status, pageable)));
    }

    @PatchMapping("/restaurant-registers/{id}/review")
    public ResponseEntity<ApiResponse<RestaurantRegisterResponse>> reviewRestaurantRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ReviewRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.reviewRestaurantRegister(user.getUserId(), id, req)));
    }

    @GetMapping("/shipper-registers")
    public ResponseEntity<ApiResponse<PageResponse<ShipperRegisterResponse>>> listShipperRegisters(
            @RequestParam(required = false) RegisterStatus status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listShipperRegisters(status, pageable)));
    }

    @PatchMapping("/shipper-registers/{id}/review")
    public ResponseEntity<ApiResponse<ShipperRegisterResponse>> reviewShipperRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ReviewRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.reviewShipperRegister(user.getUserId(), id, req)));
    }
}

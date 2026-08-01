package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.response.stats.OrderStatsResponse;
import org.example.datn.DTO.response.stats.UserStatsResponse;
import org.example.datn.Service.*;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.DTO.request.report.ResolveReportRequest;
import org.example.datn.DTO.request.auth.ReviewRegisterRequest;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.DTO.response.restaurant.RestaurantRegisterResponse;
import org.example.datn.DTO.response.auth.ShipperRegisterResponse;
import org.example.datn.DTO.response.report.ReportResponse;
import org.example.datn.DTO.response.stats.StatsOverviewResponse;
import org.example.datn.DTO.response.stats.AdminInsightsResponse;
import org.example.datn.DTO.response.stats.AdminReportResponse;
import org.example.datn.DTO.response.stats.VoucherAnalyticsResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.DTO.response.order.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    private final UserService userService;

    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<StatsOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.adminOverview()));
    }

    /** Tổng quan nghiệp vụ toàn hệ thống (xu hướng, tăng trưởng, GTV theo ngày, giờ cao điểm, toàn vẹn thanh toán). */
    @GetMapping("/stats/insights")
    public ResponseEntity<ApiResponse<AdminInsightsResponse>> insights() {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.adminInsights()));
    }

    /** Báo cáo phân tích doanh thu hệ thống theo kỳ (gộp ở server) cho trang Thống kê admin. */
    @GetMapping("/stats/report")
    public ResponseEntity<ApiResponse<AdminReportResponse>> report(
            @RequestParam(required = false, defaultValue = "all") String range) {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.adminReport(range)));
    }

    /** Phân tích voucher theo kỳ (lượt dùng, chi phí giảm giá, top voucher) cho dashboard admin. */
    @GetMapping("/stats/vouchers")
    public ResponseEntity<ApiResponse<VoucherAnalyticsResponse>> voucherAnalytics(
            @RequestParam(required = false, defaultValue = "30days") String range) {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.voucherAnalytics(range)));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAdminOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String statusGroup,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listOrders(keyword, status, statusGroup, pageable)));
    }

    @GetMapping("/orders/stats")
    public ResponseEntity<ApiResponse<OrderStatsResponse>> getOrderStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getOrderStats()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(page = 0, size = 10, sort = "userId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(keyword, role, active, pageable)));
    }

    @GetMapping("/users/stats")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getUserStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getUserStats()));
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

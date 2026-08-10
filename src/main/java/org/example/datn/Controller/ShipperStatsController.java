package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.stats.ShipperInsightsResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipper/stats")
@PreAuthorize("hasRole('SHIPPER')")
@RequiredArgsConstructor
public class ShipperStatsController {

    private final StatisticsService statisticsService;

    /** Tổng hợp thu nhập tài xế (gộp ở server) cho trang Thu Nhập. */
    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<ShipperInsightsResponse>> insights(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.shipperInsights(user.getUserId())));
    }
}

package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.stats.MerchantInsightsResponse;
import org.example.datn.DTO.response.stats.MerchantStatsResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/stats")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class MerchantStatsController {

    private final StatisticsService statisticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<MerchantStatsResponse>> stats(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(
                statisticsService.merchantStats(user.getUserId(), restaurantId)));
    }

    /** Tổng quan nghiệp vụ cho dashboard (xu hướng, giờ cao điểm, khách, sức khoẻ thực đơn). */
    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<MerchantInsightsResponse>> insights(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(
                statisticsService.merchantInsights(user.getUserId(), restaurantId)));
    }
}

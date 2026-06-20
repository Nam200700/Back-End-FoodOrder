package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StatsOverviewResponse {
    private long totalUsers;
    private long totalRestaurants;
    private long totalOrders;
    private long completedOrders;
    private long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCommission;
    private BigDecimal totalMerchantNet;
    private BigDecimal totalShipperShare;
    private BigDecimal commissionRate;
}

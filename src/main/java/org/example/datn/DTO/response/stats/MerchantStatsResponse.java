package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MerchantStatsResponse {
    private Long restaurantId;
    private long totalOrders;
    private long completedOrders;
    private BigDecimal revenue;
    private BigDecimal subtotal;
    private BigDecimal commission;
    private BigDecimal commissionRate;
}

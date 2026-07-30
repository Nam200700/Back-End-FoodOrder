package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderStatsResponse {
    private long totalOrders;
    private long completedOrders;
    private long cancelledOrders;
    private long processingOrders;
    private long deliveringOrders;
}

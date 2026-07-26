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

    // ─── Bổ sung cho dashboard merchant (rating toàn cục + KPI vận hành) ───
    private long cancelledOrders;     // số đơn bị huỷ
    private long pendingOrders;       // số đơn đang chờ duyệt
    private Double avgRating;         // điểm đánh giá trung bình TOÀN CỤC (không phải theo trang)
    private long reviewsCount;        // tổng số đánh giá
    private BigDecimal avgOrderValue; // AOV = doanh thu thực nhận / đơn hoàn tất
}

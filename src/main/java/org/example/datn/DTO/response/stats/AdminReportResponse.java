package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * BÁO CÁO PHÂN TÍCH DOANH THU HỆ THỐNG (trang Thống kê admin) — gộp TOÀN BỘ ở server theo cửa sổ thời gian.
 * Thay cho việc FE tải size=2000 đơn + size=1500 user rồi tính client (mất ~3s). Số chính xác, không cap.
 */
@Data
@Builder
public class AdminReportResponse {

    private String range;
    private BigDecimal commissionRate;

    // ─── Dòng tiền toàn sàn trong kỳ (đơn hoàn tất, loại refund) ───
    private BigDecimal gtv;          // tổng giao dịch (gồm ship)
    private BigDecimal subtotal;     // tổng tiền món ăn
    private BigDecimal commission;   // hoa hồng sàn = subtotal * rate
    private BigDecimal merchantNet;  // quán thực nhận = subtotal - commission
    private BigDecimal shipping;     // tổng cước ship (shipper)
    private BigDecimal aov;

    private long totalOrders;
    private long completedOrders;
    private long cancelledOrders;
    private long uniqueCustomers;

    private List<DayPoint> daily;       // {date, gtv, subtotal, orders}
    private List<Bucket> paymentDist;
    private List<Bucket> statusDist;
    private List<TopRestaurant> topRestaurants;

    @Data @Builder
    public static class DayPoint {
        private String date;
        private BigDecimal gtv;
        private BigDecimal subtotal;
        private long orders;
    }

    @Data @Builder
    public static class Bucket {
        private String key;
        private long count;
        private BigDecimal amount;
    }

    @Data @Builder
    public static class TopRestaurant {
        private String name;
        private long orders;
        private BigDecimal subtotal;
        private BigDecimal commission;
        private BigDecimal netShare;
    }
}

package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Số liệu TỔNG QUAN NGHIỆP VỤ TOÀN HỆ THỐNG cho dashboard admin.
 * Tính TRỰC TIẾP ở DB (không tải danh sách đơn rồi tính client-side vốn bị chặn size=2000)
 * để con số toàn sàn luôn CHÍNH XÁC — admin quản trị cả hệ thống, số sai là hậu quả nặng.
 * Tách khỏi báo cáo tài chính chi tiết ở /admin/stats/overview (giữ nguyên, không đụng).
 */
@Data
@Builder
public class AdminInsightsResponse {

    // ─── Xu hướng 7 ngày qua vs 7 ngày trước (GTV & đơn hoàn tất) → FE tính Δ% ───
    private BigDecimal gmv7d;
    private BigDecimal gmvPrev7d;
    private long orders7d;
    private long ordersPrev7d;

    // ─── Tăng trưởng thành viên/đối tác (theo created_at, không cap) ───
    private long newUsers7d;
    private long newUsers30d;
    private long newRestaurants30d;
    private long newShippers30d;

    // ─── Chuỗi GTV theo ngày (mặc định 30 ngày gần nhất) — tính ở SERVER, không giới hạn ───
    private List<DayBucket> dailyGmv;

    // ─── Giờ cao điểm toàn hệ thống: số đơn hoàn tất gom theo giờ trong ngày ───
    private List<HourBucket> peakHours;

    // ─── Toàn vẹn thanh toán (đếm theo PaymentStatus toàn hệ thống) ───
    private long paidOrders;      // đã thanh toán
    private long pendingPayment;  // chờ thanh toán
    private long refundedOrders;  // đã hoàn tiền (rủi ro)
    private long failedPayments;  // thanh toán thất bại (rủi ro)

    @Data
    @Builder
    public static class DayBucket {
        private String date;      // yyyy-MM-dd
        private BigDecimal gmv;   // tổng GTV đơn hoàn tất (loại refund) trong ngày
        private long orders;      // số đơn hoàn tất trong ngày
    }

    @Data
    @Builder
    public static class HourBucket {
        private int hour;   // 0..23
        private long count; // số đơn hoàn tất trong khung giờ đó
    }
}

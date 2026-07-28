package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Số liệu TỔNG QUAN NGHIỆP VỤ cho dashboard merchant (tách khỏi báo cáo tài chính ở /merchant/stats).
 * Tính trên TOÀN BỘ đơn (không bị chặn size như tính client-side) để owner nhìn phát biết sức khoẻ kinh doanh.
 */
@Data
@Builder
public class MerchantInsightsResponse {

    // ─── Xu hướng 7 ngày qua vs 7 ngày trước (subtotal đơn hoàn tất) → FE tính Δ% ───
    private BigDecimal revenue7d;
    private BigDecimal revenuePrev7d;
    private long orders7d;
    private long ordersPrev7d;

    // ─── Giờ cao điểm: số đơn hoàn tất gom theo giờ trong ngày ───
    private List<HourBucket> peakHours;

    // ─── Khách hàng (trên đơn hoàn tất) ───
    private long uniqueCustomers;      // số khách đã mua (distinct)
    private long returningCustomers;   // khách có >= 2 đơn (quay lại)
    private long newCustomers30d;      // khách có đơn đầu tiên trong 30 ngày

    // ─── Sức khoẻ thực đơn ───
    private long menuTotal;       // tổng số món
    private long menuAvailable;   // đang bán (hiện + còn hàng)
    private long menuOutOfStock;  // hiện nhưng tạm hết hàng
    private long menuHidden;      // đang ẩn khỏi thực đơn
    private long menuNoSales;     // món đang bán nhưng chưa có lượt bán nào

    @Data
    @Builder
    public static class HourBucket {
        private int hour;   // 0..23
        private long count; // số đơn hoàn tất trong khung giờ đó
    }
}

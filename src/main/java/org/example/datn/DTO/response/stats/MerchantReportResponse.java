package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * BÁO CÁO TÀI CHÍNH NHÀ HÀNG (trang Thống kê owner) — gộp TOÀN BỘ ở server theo cửa sổ thời gian.
 * Trước đây FE tải size=2000 đơn rồi tính client (chậm ~vài giây, lại bị cap). Giờ trả sẵn số đã gộp (vài KB).
 */
@Data
@Builder
public class MerchantReportResponse {

    private String range;              // 7days | 30days | thisMonth | all
    private BigDecimal commissionRate; // để FE hiển thị % và suy ra thực nhận

    // ─── Tài chính trong kỳ (đơn hoàn tất, loại refund) ───
    private BigDecimal gtv;         // tổng giá trị đơn (gồm ship)
    private BigDecimal subtotal;    // tiền món ăn
    private BigDecimal commission;  // hoa hồng sàn = subtotal * rate
    private BigDecimal earnings;    // quán thực nhận = subtotal - commission
    private BigDecimal shipping;    // cước ship (thuộc shipper)
    private BigDecimal aov;         // giá trị đơn TB (theo subtotal / đơn hoàn tất)

    // ─── Đếm đơn / khách trong kỳ ───
    private long totalOrders;       // mọi trạng thái
    private long completedOrders;
    private long cancelledOrders;
    private long uniqueCustomers;

    // ─── Chuỗi ngày (biểu đồ xu hướng) ───
    private List<DayPoint> daily;

    // ─── Phân bố ───
    private List<Bucket> paymentDist; // theo trạng thái thanh toán
    private List<Bucket> statusDist;  // theo trạng thái đơn

    // ─── Top món bán chạy ───
    private List<TopFood> topFoods;

    @Data @Builder
    public static class DayPoint {
        private String date;        // yyyy-MM-dd
        private BigDecimal subtotal;// tiền món (đơn hoàn tất) trong ngày
        private long orders;        // số đơn (mọi trạng thái) trong ngày
    }

    @Data @Builder
    public static class Bucket {
        private String key;         // enum name (COMPLETED, PAID, ...)
        private long count;
        private BigDecimal amount;
    }

    @Data @Builder
    public static class TopFood {
        private String name;
        private long qty;
        private BigDecimal revenue;
    }
}

package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PHÂN TÍCH VOUCHER cho dashboard admin — tính THẲNG ở DB từ đơn hoàn tất có gắn voucher
 * (Order.voucher != null, Order.discountAmount). Không bịa số: mọi con số suy từ đơn thật.
 */
@Data
@Builder
public class VoucherAnalyticsResponse {

    private String range;

    // Tổng quan kho voucher (toàn hệ thống, không theo kỳ)
    private long totalVouchers;
    private long activeVouchers;

    // Trong kỳ [from, to)
    private long redeemedOrders;             // số đơn hoàn tất có dùng voucher
    private BigDecimal discountCost;         // tổng tiền đã giảm cho khách (chi phí khuyến mãi)
    private BigDecimal voucherRevenue;       // GTV các đơn có voucher (doanh thu kéo về nhờ voucher)
    private BigDecimal avgDiscountPerOrder;  // tiền giảm trung bình mỗi đơn có voucher

    private List<TopVoucher> topVouchers;    // top voucher theo lượt dùng
    private List<DayUsage> dailyUsage;       // lượt dùng theo ngày (cho biểu đồ)

    @Data
    @Builder
    public static class TopVoucher {
        private String code;
        private String name;
        private long uses;
        private BigDecimal discount;
    }

    @Data
    @Builder
    public static class DayUsage {
        private String date;   // yyyy-MM-dd
        private long uses;
        private BigDecimal discount;
    }
}

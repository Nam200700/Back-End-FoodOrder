package org.example.datn.DTO.response.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tổng hợp thu nhập tài xế (gộp ở server) cho trang Thu Nhập của shipper.
 * Thay cho việc FE tải size=1000 rồi tự cộng — số liệu đúng kể cả khi vượt 1000 đơn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipperInsightsResponse {
    private long totalEarnings;
    private int completedCount;

    private long todayEarnings;
    private int todayCount;

    private long week7dEarnings;
    private int week7dCount;

    private long thisWeekEarnings;

    private long thisMonthEarnings;
    private int thisMonthCount;
    private long lastMonthEarnings;
    private int monthDelta; // % so với tháng trước

    private int activeDayCount;      // số ngày có chạy đơn trong tháng này
    private long avgPerActiveDay;

    private String bestWeekday;      // T2..CN kiếm nhiều nhất (cả lịch sử)
    private long bestWeekdayAmount;
    private int peakHourIdx;         // giờ 0-23 kiếm nhiều nhất
    private long peakHourAmount;
    private long maxFee;             // đơn phí cao nhất

    private double rating;           // điểm TB thật (shipper.avgRating)
    private int ratedCount;

    private List<DayAmount> daily;   // thu nhập tuần này theo thứ (T2..CN)
    private List<MonthAmount> monthly; // 6 tháng gần đây

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayAmount {
        private String day;
        private long amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthAmount {
        private String label;
        private long amount;
    }
}

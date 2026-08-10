package org.example.datn.DTO.response.review;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Tóm tắt đánh giá tài xế — gộp TOÀN BỘ ở server (điểm TB, phân bố sao, % hài lòng,
 * 30 ngày gần đây, lời khen/điểm cần cải thiện) để trang Đánh Giá KHÔNG phải tải hết
 * size=1000 rồi tính client (dễ nghẽn). Danh sách review vẫn phân trang riêng.
 */
@Data
@Builder
public class ShipperReviewSummaryResponse {
    private long total;
    private double avg;
    private List<StarCount> distribution; // 5★ → 1★
    private long positiveCount;           // 4–5★ (cho % hài lòng)
    private long recentCount;             // 30 ngày gần đây
    private double recentAvg;
    private long withImageCount;
    private List<Phrase> compliments;     // top lời khen
    private List<Phrase> complaints;      // top điểm cần cải thiện

    @Data
    @Builder
    public static class StarCount {
        private int star;
        private long count;
    }

    @Data
    @Builder
    public static class Phrase {
        private String text;
        private long count;
    }
}

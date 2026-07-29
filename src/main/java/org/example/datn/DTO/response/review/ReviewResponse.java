package org.example.datn.DTO.response.review;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewResponse {
    private Long reviewId;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private Long restaurantId;
    private Integer restaurantRating;
    private String restaurantComment;
    private Integer shipperRating;
    private String shipperComment;
    private String merchantReply;
    private LocalDateTime createdAt;
    private LocalDateTime repliedAt;

    private List<String> images;
}


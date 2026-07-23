package org.example.datn.DTO.request.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateReviewRequest {

    @NotNull(message = "orderId không được để trống")
    private Long orderId;

//    @NotNull(message = "Đánh giá quán không được để trống")
    @Min(value = 1, message = "Đánh giá từ 1 đến 5")
    @Max(value = 5, message = "Đánh giá từ 1 đến 5")
    private Integer restaurantRating;

    private String restaurantComment;

    @Min(value = 1, message = "Đánh giá từ 1 đến 5")
    @Max(value = 5, message = "Đánh giá từ 1 đến 5")
    private Integer shipperRating;

    private String shipperComment;

    private List<String> images;
}

package org.example.datn.DTO.response.food;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FoodResponse {
    private Long id;
    private Long restaurantId;
    private Long categoryId;
    private String foodName;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Boolean status;
    private Boolean isAvailable;
    private java.lang.Integer orderCount;
}

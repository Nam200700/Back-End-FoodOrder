package org.example.datn.DTO.request.food;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateFoodRequest {
    private Long categoryId;
    private String foodName;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Boolean status;
    private Boolean isAvailable;
}

package org.example.datn.DTO.response.restaurant;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RestaurantResponse {
    private Long id;
    private String restaurantName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private String description;
    private String imageUrl;
    private Boolean status;
    private String statusDetail;
    private Long ownerId;
    private Long restaurantId;
    private Double rating;
    private Integer reviewsCount;
    private Integer orderCount;
}


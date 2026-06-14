package org.example.datn.DTO.request.restaurant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateRestaurantRequest {

    @NotBlank(message = "Tên quán không được để trống")
    private String restaurantName;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private String description;
    private String imageUrl;
}

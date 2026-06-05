package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OwnerReRegisterRequest {
    @NotBlank(message = "Tên quán không được để trống")
    private String restaurantName;

    @NotBlank(message = "Địa chỉ quán không được để trống")
    private String restaurantAddress;

    private java.math.BigDecimal restaurantLatitude;
    private java.math.BigDecimal restaurantLongitude;

    @NotBlank(message = "Số điện thoại quán không được để trống")
    private String restaurantPhone;

    private String restaurantDescription;
    private String restaurantImageUrl;
}

package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 50, message = "Mật khẩu từ 6 đến 50 ký tự")
    private String password;

    private String role;

    // Owner registration additional info
    private String restaurantName;
    private String restaurantAddress;
    private java.math.BigDecimal restaurantLatitude;
    private java.math.BigDecimal restaurantLongitude;
    private String restaurantPhone;
    private String restaurantDescription;
    private String restaurantImageUrl;

    // Shipper registration additional info
    private String idCard;
    private String vehicleType; // Enum String
    private String licensePlate;
}

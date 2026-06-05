package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordSendOtpRequest {

    @NotBlank(message = "Số điện thoại hoặc Email không được để trống")
    private String phoneOrEmail;

    @NotBlank(message = "Phương thức nhận OTP không được để trống")
    private String method; // SMS hoặc EMAIL
}

package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordResetRequest {

    @NotBlank(message = "Số điện thoại hoặc Email không được để trống")
    private String phoneOrEmail;

    @NotBlank(message = "Mã OTP không được để trống")
    private String otpCode;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    private String newPassword;
}

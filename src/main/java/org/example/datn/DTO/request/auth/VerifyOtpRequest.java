package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.OtpPurpose;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    @NotBlank(message = "Mã OTP không được để trống")
    private String code;

    @NotNull(message = "Mục đích OTP không được để trống")
    private OtpPurpose purpose;
}

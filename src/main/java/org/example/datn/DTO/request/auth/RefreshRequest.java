package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken không được để trống")
    private String refreshToken;
}

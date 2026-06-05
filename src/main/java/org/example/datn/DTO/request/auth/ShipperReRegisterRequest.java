package org.example.datn.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipperReRegisterRequest {
    @NotBlank(message = "Số CMTND/CCCD không được để trống")
    private String idCard;

    @NotBlank(message = "Loại phương tiện không được để trống")
    private String vehicleType;

    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate;
}

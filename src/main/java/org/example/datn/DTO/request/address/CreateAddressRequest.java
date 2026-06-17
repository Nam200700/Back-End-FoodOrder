package org.example.datn.DTO.request.address;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;


@Data
public class CreateAddressRequest {
    private String label;

    @NotNull(message = "Địa chỉ không được để trống")
    private String address;

    @NotNull(message = "Vĩ độ không được để trống")
    private BigDecimal latitude;
    @NotNull(message = "Tọa độ không được để trống")
    private BigDecimal longitude;

    private boolean isDefault;
}

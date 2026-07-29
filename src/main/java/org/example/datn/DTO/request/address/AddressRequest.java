package org.example.datn.DTO.request.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {
    private String label;

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    @NotNull(message = "Vĩ độ (latitude) không được để trống")
    private BigDecimal latitude;

    @NotNull(message = "Kinh độ (longitude) không được để trống")
    private BigDecimal longitude;

    private Boolean isDefault;
}
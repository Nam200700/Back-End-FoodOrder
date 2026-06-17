package org.example.datn.DTO.response.address;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    private Long addressId;
    private String label;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean isDefault;
}

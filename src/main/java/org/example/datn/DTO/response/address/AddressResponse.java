package org.example.datn.DTO.response.address;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddressResponse {
    private Long addressId;
    private String label;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean isDefault;
}

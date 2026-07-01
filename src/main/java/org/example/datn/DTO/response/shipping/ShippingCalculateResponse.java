package org.example.datn.DTO.response.shipping;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingCalculateResponse {
    private double distanceKm;
    private long shippingFee;
}
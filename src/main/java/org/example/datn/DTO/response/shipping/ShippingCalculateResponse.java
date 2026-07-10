package org.example.datn.DTO.response.shipping;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShippingCalculateResponse {
    private Long restaurantId;
    private double distanceKm;
    private double durationMinutes;
    private long shippingFee;
}
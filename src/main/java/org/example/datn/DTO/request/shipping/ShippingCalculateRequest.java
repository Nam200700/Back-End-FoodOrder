package org.example.datn.DTO.request.shipping;

import lombok.Data;

import java.util.List;

@Data
public class ShippingCalculateRequest {
    private List<Long> restaurantIds;
    private double deliveryLat;
    private double deliveryLng;
}

package org.example.datn.DTO.response.shipping;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShippingRouteResponse {

    private double distanceKm;

    private double durationMinutes;

    private List<List<Double>> coordinates;
}
package org.example.datn.DTO.response.shipping;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RouteInfoResponse {
    private double distanceKm;
    private double durationMinutes;
    private List<List<Double>> coordinates;
}

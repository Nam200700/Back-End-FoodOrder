package org.example.datn.DTO.response.shipping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RouteInfoResponse {
    private double distanceKm;
    private double durationMinutes;
    private List<List<Double>> coordinates;
}

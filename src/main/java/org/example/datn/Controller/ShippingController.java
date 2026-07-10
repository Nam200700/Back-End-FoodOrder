package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.shipping.ShippingCalculateRequest;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/calculate")
    public ResponseEntity<ApiResponse<List<ShippingCalculateResponse>>> calculate(ShippingCalculateRequest request ) {
        return ResponseEntity.ok(ApiResponse.ok(
                shippingService.calculate(request.getRestaurantIds(), request.getDeliveryLat(), request.getDeliveryLng())));
    }

    @GetMapping("/route")
    public ResponseEntity<ApiResponse<List<List<Double>>>> getRoute(
            @RequestParam("start_lat") double startLat,
            @RequestParam("start_lng") double startLng,
            @RequestParam("end_lat") double endLat,
            @RequestParam("end_lng") double endLng) {

        List<List<Double>> routeCoordinates = shippingService.getRouteCoordinates(startLat, startLng, endLat, endLng);
        return ResponseEntity.ok(ApiResponse.ok(routeCoordinates));
    }
}
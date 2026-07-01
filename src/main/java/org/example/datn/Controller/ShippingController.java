package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/calculate")
    public ResponseEntity<ApiResponse<ShippingCalculateResponse>> calculate(
            @RequestParam("restaurant_id") Long restaurantId,
            @RequestParam("delivery_lat") double deliveryLat,
            @RequestParam("delivery_lng") double deliveryLng) {
        return ResponseEntity.ok(ApiResponse.ok(
                shippingService.calculate(restaurantId, deliveryLat, deliveryLng)));
    }
}
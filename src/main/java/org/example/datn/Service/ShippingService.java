package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.DTO.response.shipping.RouteInfoResponse;
import org.example.datn.domain.Restaurant;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final RestaurantRepository restaurantRepository;
    private final RouteService routeService; // truy vấn tuyến đường ORS (đã cache theo toạ độ)

    @Transactional(readOnly = true)
    public List<ShippingCalculateResponse> calculate(List<Long> restaurantIds, double deliveryLat, double deliveryLng) {
        // Nạp 1 lần mọi quán trong giỏ thay vì findByIdOrThrow mỗi vòng lặp (bỏ N query).
        Map<Long, Restaurant> byId = new HashMap<>();
        for (Restaurant r : restaurantRepository.findAllById(restaurantIds)) {
            byId.put(r.getRestaurantId(), r);
        }

        List<ShippingCalculateResponse> result = new ArrayList<>(restaurantIds.size());
        for (Long restaurantId : restaurantIds) {
            Restaurant restaurant = byId.get(restaurantId);
            if (restaurant == null) {
                throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND);
            }
            if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
                throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa có tọa độ để tính phí ship");
            }

            // Route được cache theo cặp toạ độ (quán cố định) → thường chỉ gọi ORS lần đầu.
            RouteInfoResponse routeInfo = routeService.getRouteInfo(
                    restaurant.getLatitude().doubleValue(), restaurant.getLongitude().doubleValue(),
                    deliveryLat, deliveryLng
            );

            long shippingFee = ShippingFeeCalculator.calculate(routeInfo.getDistanceKm());
            result.add(
                    ShippingCalculateResponse.builder()
                            .restaurantId(restaurantId)
                            .distanceKm(routeInfo.getDistanceKm())
                            .durationMinutes(routeInfo.getDurationMinutes())
                            .shippingFee(shippingFee)
                            .build()
            );
        }
        return result;
    }

    public RouteInfoResponse getRouteCoordinates(double startLat, double startLng, double endLat, double endLng) {
        RouteInfoResponse routeInfo = routeService.getRouteInfo(startLat, startLng, endLat, endLng);

        return RouteInfoResponse.builder()
                .distanceKm(routeInfo.getDistanceKm())
                .durationMinutes(routeInfo.getDurationMinutes())
                .coordinates(routeInfo.getCoordinates())
                .build();
    }

    public double getDistanceKm(double startLat, double startLng, double endLat, double endLng) {
        return routeService.getRouteInfo(startLat, startLng, endLat, endLng).getDistanceKm();
    }
}
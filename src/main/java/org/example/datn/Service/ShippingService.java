package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.util.HaversineCalculator;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public ShippingCalculateResponse calculate(Long restaurantId, double deliveryLat, double deliveryLng) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa có tọa độ để tính phí ship");
        }

        double distanceKm = HaversineCalculator.distanceKm(
                restaurant.getLatitude().doubleValue(),
                restaurant.getLongitude().doubleValue(),
                deliveryLat, deliveryLng);

        long fee = ShippingFeeCalculator.calculate(distanceKm);

        return ShippingCalculateResponse.builder()
                .distanceKm(distanceKm)
                .shippingFee(fee)
                .build();
    }
}
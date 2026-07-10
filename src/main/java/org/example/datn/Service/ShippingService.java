package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.domain.Restaurant;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final RestaurantRepository restaurantRepository;
    private final RestTemplate restTemplate;

    @Value("${openrouteservice.api.key}")
    private String openRouteServiceApiKey;

    @Transactional(readOnly = true)
    public List<ShippingCalculateResponse> calculate(List<Long> restaurantIds, double deliveryLat, double deliveryLng) {
        List<ShippingCalculateResponse> result = new ArrayList<>();
        for (Long restaurantId : restaurantIds) {
            Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
            if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
                throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa có tọa độ để tính phí ship");
            }

            double resLat = restaurant.getLatitude().doubleValue();
            double resLng = restaurant.getLongitude().doubleValue();
            double distanceKm = 0;
            double durationMinutes = 0;

            try {
                // OpenRouteService yêu cầu format tọa độ là: start=lng,lat & end=lng,lat
                String url = String.format("https://api.openrouteservice.org/v2/directions/driving-car?api_key=%s&start=%f,%f&end=%f,%f",
                        openRouteServiceApiKey, resLng, resLat, deliveryLng, deliveryLat);

                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> features = (List<Map<String, Object>>) response.getBody().get("features");
                    if (features != null && !features.isEmpty()) {
                        Map<String, Object> properties = (Map<String, Object>) features.get(0).get("properties");
                        Map<String, Object> summary = (Map<String, Object>) properties.get("summary");

                        double distanceMeters = ((Number) summary.get("distance")).doubleValue();
                        double durationSeconds = ((Number) summary.get("duration")).doubleValue();

                        distanceKm = distanceMeters / 1000.0;
                        durationMinutes = durationSeconds / 60.0;
                    }
                }
            } catch (Exception e) {
                log.error("Lỗi khi gọi API OpenRouteService: {}", e.getMessage());
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Không thể tính toán quãng đường lúc này");
            }
            long fee = ShippingFeeCalculator.calculate(distanceKm);
            result.add(ShippingCalculateResponse.builder()
                    .restaurantId(restaurantId)
                    .distanceKm(distanceKm)
                    .shippingFee(fee)
                    .durationMinutes(durationMinutes)
                    .build()
            );
        }
        return result;
    }
}
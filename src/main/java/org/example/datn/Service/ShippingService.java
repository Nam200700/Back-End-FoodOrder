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
import java.util.Arrays;
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

    public List<List<Double>> getRouteCoordinates(double startLat, double startLng, double endLat, double endLng) {
        try {
            // Lưu ý: OpenRouteService nhận tọa độ theo thứ tự (lng, lat)
            String url = String.format("https://api.openrouteservice.org/v2/directions/driving-car?api_key=%s&start=%f,%f&end=%f,%f",
                    openRouteServiceApiKey, startLng, startLat, endLng, endLat);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> features = (List<Map<String, Object>>) response.getBody().get("features");
                if (features != null && !features.isEmpty()) {
                    Map<String, Object> geometry = (Map<String, Object>) features.get(0).get("geometry");
                    List<List<Number>> coordinates = (List<List<Number>>) geometry.get("coordinates");

                    // API trả về [lng, lat], nhưng Leaflet của Frontend vẽ cần [lat, lng]
                    // Ta đảo ngược lại luôn ở Backend cho tiện
                    return coordinates.stream()
                            .map(coord -> Arrays.asList(coord.get(1).doubleValue(), coord.get(0).doubleValue()))
                            .toList();
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi lấy toạ độ đường đi từ OpenRouteService: {}", e.getMessage());
        }
        return List.of(); // Trả về mảng rỗng nếu lỗi
    }
}
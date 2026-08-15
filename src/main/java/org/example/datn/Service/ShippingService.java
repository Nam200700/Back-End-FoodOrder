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
        List<ShippingCalculateResponse> result = new ArrayList<>(restaurantIds.size());
        for (Long restaurantId : restaurantIds) {
            Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
            if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
                throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa có tọa độ để tính phí ship");
            }

            RouteInfoResponse routeInfo = getRouteInfo(
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
        RouteInfoResponse routeInfo = getRouteInfo(
                startLat, startLng,
                endLat, endLng
        );

        return RouteInfoResponse.builder()
                .distanceKm(routeInfo.getDistanceKm())
                .durationMinutes(routeInfo.getDurationMinutes())
                .coordinates(routeInfo.getCoordinates())
                .build();
    }

    public double getDistanceKm(double startLat, double startLng, double endLat, double endLng) {
        return getRouteInfo(startLat, startLng, endLat, endLng).getDistanceKm();
    }

    private RouteInfoResponse getRouteInfo(double startLat, double startLng, double endLat, double endLng) {
        try {
            String url = String.format(
                    "https://api.openrouteservice.org/v2/directions/driving-car" +
                            "?api_key=%s" +
                            "&start=%f,%f" +
                            "&end=%f,%f" +
                            "&preference=fastest" +
                            "&instructions=false",
                    decodeIfBase64(openRouteServiceApiKey),
                    startLng, startLat,
                    endLng, endLat
            );

            log.info("ORS URL: {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(ErrorCode.INTERNAL_ERROR,"Không nhận được dữ liệu từ OpenRouteService");
            }

            List<Map<String, Object>> features = (List<Map<String, Object>>) response.getBody().get("features");
            if (features == null || features.isEmpty()) {
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Không tìm thấy tuyến đường");
            }

            Map<String, Object> feature = features.get(0);
            Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
            Map<String, Object> summary = (Map<String, Object>) properties.get("summary");
            double distanceKm = ((Number) summary.get("distance")).doubleValue() / 1000.0;
            double durationMinutes = ((Number) summary.get("duration")).doubleValue() / 60.0;

            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
            List<List<Number>> coordinates = (List<List<Number>>) geometry.get("coordinates");

            List<List<Double>> routeCoordinates = coordinates.stream()
                    .map(coord -> Arrays.asList(
                            coord.get(1).doubleValue(), coord.get(0).doubleValue()))
                    .toList();

            return RouteInfoResponse.builder()
                    .distanceKm(distanceKm)
                    .durationMinutes(durationMinutes)
                    .coordinates(routeCoordinates)
                    .build();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi gọi OpenRouteService", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể lấy dữ liệu từ OpenRouteService");
        }
    }

    private String decodeIfBase64(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.matches("^[a-zA-Z0-9+/]*={0,2}$") && trimmed.length() % 4 == 0) {
                byte[] decoded = java.util.Base64.getDecoder().decode(trimmed);
                String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                if (decodedStr.chars().allMatch(c -> c >= 32 && c < 127)) {
                    return decodedStr;
                }
            }
            return value;
        } catch (Exception e) {
            return value;
        }
    }
}
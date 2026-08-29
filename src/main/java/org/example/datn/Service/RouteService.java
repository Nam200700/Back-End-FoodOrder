package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.DTO.response.shipping.RouteInfoResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.util.HaversineCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Truy vấn tuyến đường (khoảng cách/thời gian/polyline) từ OpenRouteService.
 *
 * Tách riêng khỏi ShippingService để CACHE được: toạ độ quán cố định, địa chỉ giao
 * hầu như không đổi giữa các lần tính → cùng cặp toạ độ cho cùng kết quả. Mỗi lần
 * gọi ORS là 1 request HTTP ngoài (chậm + có rate limit), nên cache-aside ở đây cắt
 * phần lớn lời gọi lặp (vòng lặp nhiều quán, đổi địa chỉ qua lại, mở lại giỏ hàng).
 *
 * Key làm tròn ~3 số lẻ (~110m) để cùng một khu vực giao vẫn trúng cache.
 * (Phải nằm ở bean RIÊNG: @Cacheable bị bỏ qua nếu gọi nội bộ cùng bean.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private final RestTemplate restTemplate;

    @Value("${openrouteservice.api.key}")
    private String openRouteServiceApiKey;

    @Cacheable(
            value = "routeInfo",
            key = "T(java.lang.String).format('%.3f,%.3f,%.3f,%.3f', #startLat, #startLng, #endLat, #endLng)")
    public RouteInfoResponse getRouteInfo(double startLat, double startLng, double endLat, double endLng) {
        try {
            String url = String.format(
                    "https://api.openrouteservice.org/v2/directions/driving-car" +
                            "?api_key=%s" +
                            "&start=%f,%f" +
                            "&end=%f,%f" +
                            "&preference=fastest" +
                            "&instructions=false",
                    openRouteServiceApiKey,
                    startLng, startLat,
                    endLng, endLat
            );

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            // ORS không trả về dữ liệu hợp lệ
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OpenRouteService không trả về dữ liệu hợp lệ. Sử dụng Haversine.");
                return createHaversineFallback(startLat, startLng, endLat, endLng);
            }

            List<Map<String, Object>> features = (List<Map<String, Object>>) response.getBody().get("features");

            // Không tìm thấy route
            if (features == null || features.isEmpty()) {
                log.warn("OpenRouteService không tìm thấy tuyến đường. Sử dụng Haversine.");
                return createHaversineFallback(startLat, startLng, endLat, endLng);
            }

            Map<String, Object> feature = features.get(0);

            Map<String, Object> properties = (Map<String, Object>) feature.get("properties");

            Map<String, Object> summary = (Map<String, Object>) properties.get("summary");

            double distanceKm = ((Number) summary.get("distance")).doubleValue() / 1000.0;

            double durationMinutes = ((Number) summary.get("duration")).doubleValue() / 60.0;

            Map<String, Object> geometry =
                    (Map<String, Object>) feature.get("geometry");

            List<List<Number>> coordinates = (List<List<Number>>) geometry.get("coordinates");

            List<List<Double>> routeCoordinates = coordinates.stream()
                    .map(coord -> Arrays.asList(
                            coord.get(1).doubleValue(),
                            coord.get(0).doubleValue()
                    ))
                    .toList();

            log.info(
                    "Lấy route từ OpenRouteService thành công: {} km, {} phút",
                    distanceKm,
                    durationMinutes
            );

            return RouteInfoResponse.builder()
                    .distanceKm(distanceKm)
                    .durationMinutes(durationMinutes)
                    .coordinates(routeCoordinates)
                    .build();

        } catch (Exception e) {
            log.warn("Không thể gọi OpenRouteService: {}. Sử dụng Haversine.", e.getMessage());
            return createHaversineFallback(startLat, startLng, endLat, endLng);
        }
    }

    /**
     * Fallback khi OpenRouteService không hoạt động.
     *
     * Haversine tính khoảng cách đường chim bay.
     * Coordinates trả về 2 điểm để frontend vẫn vẽ được tuyến
     */
    private RouteInfoResponse createHaversineFallback(double startLat, double startLng,
                                                        double endLat, double endLng) {

        double distanceKm = HaversineCalculator.distanceKm(startLat, startLng, endLat, endLng);

        List<List<Double>> fallbackCoordinates = List.of(
                Arrays.asList(startLat, startLng),
                Arrays.asList(endLat, endLng)
        );

        log.info("Fallback Haversine: {} km", distanceKm);

        return RouteInfoResponse.builder()
                .distanceKm(distanceKm)
                .durationMinutes(0.0)
                .coordinates(fallbackCoordinates)
                .build();
    }
}

package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.DTO.response.shipping.RouteInfoResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
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
                    decodeIfBase64(openRouteServiceApiKey),
                    startLng, startLat,
                    endLng, endLat
            );

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(ErrorCode.INTERNAL_ERROR, "Không nhận được dữ liệu từ OpenRouteService");
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

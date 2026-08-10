package org.example.datn.mapper;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RestaurantMapper {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    /** Map 1 quán — tự truy vấn rating/đơn (dùng cho chi tiết 1 quán). Với DANH SÁCH,
     *  service nên gộp số liệu theo lô rồi gọi overload bên dưới để tránh N+1. */
    public RestaurantResponse toResponse(Restaurant r) {
        if (r == null) {
            return null;
        }

        Double avgRating = reviewRepository.findAverageRatingByRestaurantId(r.getRestaurantId());
        long count = reviewRepository.countByRestaurantRestaurantId(r.getRestaurantId());
        long ordersCount = orderRepository.countByRestaurantRestaurantIdAndOrderStatus(
                r.getRestaurantId(),
                OrderStatus.COMPLETED
        );
        return toResponse(r, avgRating, count, ordersCount);
    }

    /** Map 1 quán với số liệu ĐÃ tính sẵn (không truy vấn thêm) — dùng khi map cả trang theo lô. */
    public RestaurantResponse toResponse(Restaurant r, Double avgRating, long count, long ordersCount) {
        if (r == null) {
            return null;
        }

        if (avgRating == null) {
            avgRating = 5.0;
        }
        avgRating = Math.round(avgRating * 10.0) / 10.0;

        return RestaurantResponse.builder()
                .id(r.getRestaurantId())
                .restaurantName(r.getRestaurantName())
                .address(r.getAddress())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .phone(r.getPhone())
                .description(r.getDescription())
                .imageUrl(r.getImageUrl())
                .status(r.getStatus())
                .statusDetail(r.getStatusDetail() != null ? r.getStatusDetail().name() : null)
                .ownerId(r.getOwner() != null ? r.getOwner().getUserId() : null)
                .restaurantId(r.getRestaurantId())
                .rating(avgRating)
                .reviewsCount((int) count)
                .orderCount((int) ordersCount)
                .opensAt(r.getOpensAt())
                .closesAt(r.getClosesAt())
                .isOpen(isRestaurantOpen(r))
                .build();
    }

    public List<RestaurantResponse> toResponseList(List<Restaurant> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private boolean isRestaurantOpen(Restaurant restaurant) {
        if (restaurant == null || restaurant.getOpensAt() == null || restaurant.getClosesAt() == null) {
            return true;
        }
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalTime opensAt = restaurant.getOpensAt();
        LocalTime closesAt = restaurant.getClosesAt();

        if (opensAt.isBefore(closesAt)) {
            // Trường hợp bình thường trong ngày (VD: 08:00 - 22:00)
            return !now.isBefore(opensAt) && !now.isAfter(closesAt);
        } else {
            // Trường hợp bán qua đêm (VD: 22:00 - 04:00 sáng hôm sau)
            return !now.isBefore(opensAt) || !now.isAfter(closesAt);
        }
    }
}

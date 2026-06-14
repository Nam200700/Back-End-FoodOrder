package org.example.datn.mapper;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RestaurantMapper {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    public RestaurantResponse toResponse(Restaurant r) {
        if (r == null) {
            return null;
        }
        
        Double avgRating = reviewRepository.findAverageRatingByRestaurantId(r.getRestaurantId());
        if (avgRating == null) {
            avgRating = 5.0;
        }
        avgRating = Math.round(avgRating * 10.0) / 10.0;
        
        long count = reviewRepository.countByRestaurantRestaurantId(r.getRestaurantId());
        
        long ordersCount = orderRepository.countByRestaurantRestaurantIdAndOrderStatus(
                r.getRestaurantId(), 
                OrderStatus.COMPLETED
        );

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
                .build();
    }

    public List<RestaurantResponse> toResponseList(List<Restaurant> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }
}

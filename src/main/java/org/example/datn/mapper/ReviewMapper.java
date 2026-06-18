package org.example.datn.mapper;

import org.example.datn.domain.Review;
import org.example.datn.DTO.response.review.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(source = "order.orderId", target = "orderId")
    @Mapping(source = "customer.userId", target = "customerId")
    @Mapping(source = "customer.fullName", target = "customerName")
    @Mapping(source = "restaurant.restaurantId", target = "restaurantId")
    ReviewResponse toResponse(Review review);
}
package org.example.datn.mapper;

import org.example.datn.domain.Review;
import org.example.datn.domain.ReviewImage;
import org.example.datn.DTO.response.review.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(source = "order.orderId", target = "orderId")
    @Mapping(source = "customer.userId", target = "customerId")
    @Mapping(source = "customer.fullName", target = "customerName")
    @Mapping(source = "restaurant.restaurantId", target = "restaurantId")
    @Mapping(source = "images", target = "images", qualifiedByName = "mapImagesToStrings")
    ReviewResponse toResponse(Review review);

    @Named("mapImagesToStrings")
    default List<String> mapImagesToStrings(List<ReviewImage> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .sorted(Comparator.comparing(ReviewImage::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ReviewImage::getImageUrl)
                .collect(Collectors.toList());
    }
}
package org.example.datn.mapper;

import org.example.datn.domain.Category;
import org.example.datn.DTO.response.restaurant.CategoryResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category c) {
        if (c == null) {
            return null;
        }
        return CategoryResponse.builder()
                .categoryId(c.getCategoryId())
                .restaurantId(c.getRestaurant() != null ? c.getRestaurant().getRestaurantId() : null)
                .categoryName(c.getCategoryName())
                .displayOrder(c.getDisplayOrder())
                .build();
    }

    public List<CategoryResponse> toResponseList(List<Category> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }
}

package org.example.datn.mapper;

import org.example.datn.domain.Food;
import org.example.datn.DTO.request.food.CreateFoodRequest;
import org.example.datn.DTO.request.food.UpdateFoodRequest;
import org.example.datn.DTO.response.food.FoodResponse;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FoodMapper {

    @Mapping(source = "foodId", target = "id")
    @Mapping(source = "restaurant.restaurantId", target = "restaurantId")
    @Mapping(source = "category.categoryId", target = "categoryId")
    FoodResponse toResponse(Food food);

    List<FoodResponse> toResponseList(List<Food> foods);

    @Mapping(target = "foodId", ignore = true)
    @Mapping(target = "restaurant", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    Food toEntity(CreateFoodRequest request);

    @Mapping(target = "foodId", ignore = true)
    @Mapping(target = "restaurant", ignore = true)
    @Mapping(target = "category", ignore = true)
    void updateEntity(UpdateFoodRequest request, @MappingTarget Food food);
}

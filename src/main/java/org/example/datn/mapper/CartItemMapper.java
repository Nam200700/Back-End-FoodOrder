package org.example.datn.mapper;

import org.example.datn.DTO.response.cart.CartItemResponse;
import org.example.datn.domain.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CartItemMapper {

    @Mapping(target = "foodId", source = "food.foodId")
    @Mapping(target = "foodName", source = "food.foodName")
    @Mapping(target = "price", source = "food.price")
    @Mapping(target = "foodImageUrl", source = "food.imageUrl")
    @Mapping(
            target = "lineTotal",
            expression = "java(item.getFood().getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))"
    )
    CartItemResponse toResponse(CartItem item);
}

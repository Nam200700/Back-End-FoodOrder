package org.example.datn.mapper;

import org.example.datn.DTO.response.cart.CartItemResponse;
import org.example.datn.DTO.response.cart.CartResponse;
import org.example.datn.domain.Cart;
import org.example.datn.domain.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(source = "restaurant.restaurantId", target = "restaurantId")
    @Mapping(source = "restaurant.restaurantName", target = "restaurantName")
    @Mapping(source = "restaurant.latitude", target = "latitude")
    @Mapping(source = "restaurant.longitude", target = "longitude")
    @Mapping(target = "subtotal", ignore = true)
    CartResponse toResponse(Cart cart);

    @Mapping(source = "food.foodId", target = "foodId")
    @Mapping(source = "food.foodName", target = "foodName")
    @Mapping(source = "food.price", target = "price")
    @Mapping(source = "food.imageUrl", target = "foodImageUrl")
    @Mapping(target = "lineTotal",
            expression = "java(item.getFood().getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    CartItemResponse toItemResponse(CartItem item);
}

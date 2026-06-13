package org.example.datn.mapper;

import org.example.datn.DTO.response.cart.CartItemResponse;
import org.example.datn.domain.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CartItemMapper {

    @Mapping(target = "foodId", source = "food.foodId")
    @Mapping(target = "foodName", source = "food.foodName")
    @Mapping(target = "price", source = "food.price")
    @Mapping(target = "foodImageUrl", source = "food.imageUrl")
    @Mapping(
            target = "lineTotal",
            source = ".",
            qualifiedByName = "calculateLineTotal"
    )
    CartItemResponse toResponse(CartItem item);

    @Named("calculateLineTotal")
    default BigDecimal calculateLineTotal(CartItem item) {
        return item.getFood().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}

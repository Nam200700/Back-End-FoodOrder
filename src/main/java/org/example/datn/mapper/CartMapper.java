package org.example.datn.mapper;

import org.example.datn.DTO.response.cart.CartItemResponse;
import org.example.datn.DTO.response.cart.CartResponse;
import org.example.datn.domain.Cart;
import org.example.datn.domain.CartItem;
import org.example.datn.domain.Restaurant;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Mapper(componentModel = "spring",
        uses = CartItemMapper.class
)
public interface CartMapper {

    @Mapping(target = "restaurantId",   source = "restaurant.restaurantId")
    @Mapping(target = "restaurantName", source = "restaurant.restaurantName")
    @Mapping(target = "latitude",       source = "restaurant.latitude")
    @Mapping(target = "longitude",      source = "restaurant.longitude")
    @Mapping(target = "opensAt",        source = "restaurant.opensAt")
    @Mapping(target = "closesAt",       source = "restaurant.closesAt")
    @Mapping(target = "isOpen",          expression = "java(isRestaurantOpen(cart.getRestaurant()))")
    @Mapping(target = "subtotal",       ignore = true)
    CartResponse toResponse(Cart cart);

    @AfterMapping
    default void setSubtotal(Cart cart, @MappingTarget CartResponse response) {
        BigDecimal subtotal = cart.getItems()
                .stream()
                .map(i -> i.getFood().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setSubtotal(subtotal);
    }

    default CartResponse toEmptyResponse(Restaurant restaurant) {
        return CartResponse.builder()
                .restaurantId(restaurant.getRestaurantId())
                .restaurantName(restaurant.getRestaurantName())
                .items(List.of())
                .subtotal(BigDecimal.ZERO)
                .latitude(restaurant.getLatitude())
                .longitude(restaurant.getLongitude())
                .closesAt(restaurant.getClosesAt())
                .opensAt(restaurant.getOpensAt())
                .isOpen(isRestaurantOpen(restaurant))
                .build();
    }

    default boolean isRestaurantOpen(Restaurant restaurant) {
        if (restaurant == null || restaurant.getOpensAt() == null || restaurant.getClosesAt() == null) {
            return true;
        }
        LocalTime now = LocalTime.now();
        LocalTime opensAt = restaurant.getOpensAt();
        LocalTime closesAt = restaurant.getClosesAt();

        if (opensAt.isBefore(closesAt)) {
            // Mở và đóng trong cùng một ngày (VD: 08:00 - 22:00)
            return !now.isBefore(opensAt) && !now.isAfter(closesAt);
        } else {
            // Mở cửa qua đêm (VD: 22:00 - 04:00 sáng hôm sau)
            return !now.isBefore(opensAt) || !now.isAfter(closesAt);
        }
    }
}

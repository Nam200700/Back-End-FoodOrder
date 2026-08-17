package org.example.datn.mapper;

import org.example.datn.DTO.response.order.OrderItemResponse;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.domain.Order;
import org.example.datn.domain.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.userId")
    @Mapping(target = "customerName", source = "customer.fullName")
    @Mapping(target = "customerPhone", source = "customer.phone")
    @Mapping(target = "restaurantId", source = "restaurant.restaurantId")
    @Mapping(target = "restaurantName", source = "restaurant.restaurantName")
    @Mapping(target = "shipperId", source = "shipper.userId")
    @Mapping(target = "shipperName", source = "shipper.fullName")
    @Mapping(target = "shipperPhone", source = "shipper.phone")
    @Mapping(target = "shipperAvatar", source = "shipper.avatar")
    @Mapping(target = "restaurantAddress", source = "restaurant.address")
    @Mapping(target = "groupOrderId", source = "groupOrder.groupOrderId")
    @Mapping(target = "groupHostId", source = "groupOrder.host.userId")
    @Mapping(target = "groupHostName", source = "groupOrder.host.fullName")
    OrderResponse toResponse(Order order);

    @Mapping(target = "foodId", source = "food.foodId")
    @Mapping(target = "foodImageUrl", source = "food.imageUrl")
    OrderItemResponse toItemResponse(OrderItem item);
}

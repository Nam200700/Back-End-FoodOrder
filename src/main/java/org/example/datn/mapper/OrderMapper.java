package org.example.datn.mapper;

import org.example.datn.DTO.response.order.OrderItemResponse;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.domain.Order;
import org.example.datn.domain.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "customer.userId", target = "customerId")
    @Mapping(source = "customer.fullName", target = "customerName")
    @Mapping(source = "customer.phone", target = "customerPhone")
    @Mapping(source = "restaurant.restaurantId", target = "restaurantId")
    @Mapping(source = "restaurant.restaurantName", target = "restaurantName")
    @Mapping(source = "shipper.userId", target = "shipperId")
    @Mapping(source = "shipper.fullName", target = "shipperName")
    @Mapping(source = "shipper.phone", target = "shipperPhone")
    OrderResponse toResponse(Order order);

    @Mapping(source = "food.foodId", target = "foodId")
    OrderItemResponse toItemResponse(OrderItem item);
}

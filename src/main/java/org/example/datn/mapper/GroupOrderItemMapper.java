package org.example.datn.mapper;

import org.example.datn.DTO.response.grouporder.GroupOrderItemResponse;
import org.example.datn.domain.GroupOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupOrderItemMapper {

    @Mapping(target = "groupOrderItemId", source = "groupOrderItemId")
    @Mapping(target = "memberId", source = "member.memberId")
    @Mapping(target = "memberName", source = "member.user.fullName")
    @Mapping(target = "foodId", source = "food.foodId")
    @Mapping(target = "foodName", source = "food.foodName")
    @Mapping(target = "foodImageUrl", source = "food.imageUrl")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "priceAtAdd", source = "priceAtAdd")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "lineTotal",
            expression = "java(item.getPriceAtAdd().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    GroupOrderItemResponse toResponse(GroupOrderItem item);

    List<GroupOrderItemResponse> toResponseList(List<GroupOrderItem> items);
}

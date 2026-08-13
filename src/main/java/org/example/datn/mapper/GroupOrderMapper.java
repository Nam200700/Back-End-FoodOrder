package org.example.datn.mapper;

import org.example.datn.DTO.response.grouporder.GroupOrderResponse;
import org.example.datn.domain.GroupOrder;
import org.example.datn.domain.GroupOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupOrderMapper {

    @Mapping(target = "hostId", source = "host.userId")
    @Mapping(target = "hostName", source = "host.fullName")
    @Mapping(target = "restaurantId", source = "restaurant.restaurantId")
    @Mapping(target = "restaurantName", source = "restaurant.restaurantName")
    @Mapping(target = "restaurantImageUrl", source = "restaurant.imageUrl")
    @Mapping(target = "memberCount", expression = "java(g.getMembers().size())")
    @Mapping(target = "totalItemCount", expression = "java(g.getItems().size())")
    @Mapping(target = "subtotalAmount", expression = "java(sumSubtotal(g.getItems()))")
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "inviteUrl", ignore = true)
    GroupOrderResponse toResponse(GroupOrder g);

    default BigDecimal sumSubtotal(List<GroupOrderItem> items) {
        if (items == null) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(i -> i.getPriceAtAdd()
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
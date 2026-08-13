package org.example.datn.mapper;

import org.example.datn.DTO.response.grouporder.GroupOrderMemberResponse;
import org.example.datn.domain.GroupOrderItem;
import org.example.datn.domain.GroupOrderMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", uses = GroupOrderItemMapper.class)
public interface GroupOrderMemberMapper {

    @Mapping(target = "memberId", source = "member.memberId")
    @Mapping(target = "userId", source = "member.user.userId")
    @Mapping(target = "fullName", source = "member.user.fullName")
    @Mapping(target = "avatar", source = "member.user.avatar")
    @Mapping(target = "isHost", source = "member.isHost")
    @Mapping(target = "status", source = "member.status")
    @Mapping(target = "joinedAt", source = "member.joinedAt")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "memberSubtotal", expression = "java(sumSubtotal(items))")
    GroupOrderMemberResponse toResponse(GroupOrderMember member, List<GroupOrderItem> items);

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
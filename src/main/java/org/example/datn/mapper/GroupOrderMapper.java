package org.example.datn.mapper;

import org.example.datn.DTO.response.grouporder.GroupOrderMemberResponse;
import org.example.datn.DTO.response.grouporder.GroupOrderResponse;
import org.example.datn.domain.GroupOrder;
import org.example.datn.domain.GroupOrderItem;
import org.example.datn.domain.GroupOrderMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class GroupOrderMapper {

    @Autowired
    protected GroupOrderMemberMapper memberMapper;

    @Mapping(target = "hostId", source = "host.userId")
    @Mapping(target = "hostName", source = "host.fullName")
    @Mapping(target = "restaurantId", source = "restaurant.restaurantId")
    @Mapping(target = "restaurantName", source = "restaurant.restaurantName")
    @Mapping(target = "restaurantImageUrl", source = "restaurant.imageUrl")
    @Mapping(target = "memberCount", expression = "java(g.getMembers().size())")
    @Mapping(target = "totalItemCount", expression = "java(g.getItems().size())")
    @Mapping(target = "subtotalAmount", expression = "java(sumSubtotal(g.getItems()))")
    @Mapping(target = "members", ignore = true)   // gán tay bên dưới vì cần gom theo memberId
    @Mapping(target = "orderId", ignore = true)   // set ở Service sau khi checkout
    @Mapping(target = "inviteUrl", ignore = true) // set ở Service (cần base URL FE)
    protected abstract GroupOrderResponse toResponseBase(GroupOrder g);

    /** Entry point dùng ở Service. Field-to-field do MapStruct sinh; chỉ gom List<Member> theo tay. */
    public GroupOrderResponse toResponse(GroupOrder g) {
        GroupOrderResponse res = toResponseBase(g);

        Map<Long, List<GroupOrderItem>> itemsByMember = g.getItems().stream()
                .collect(Collectors.groupingBy(i -> i.getMember().getMemberId()));

        List<GroupOrderMemberResponse> members = g.getMembers().stream()
                .sorted(Comparator.comparing(GroupOrderMember::getJoinedAt))
                .map(m -> memberMapper.toResponse(m, itemsByMember.getOrDefault(m.getMemberId(), List.of())))
                .collect(Collectors.toList());

        res.setMembers(members);
        return res;
    }

    protected BigDecimal sumSubtotal(List<GroupOrderItem> items) {
        return items.stream()
                .map(i -> i.getPriceAtAdd().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

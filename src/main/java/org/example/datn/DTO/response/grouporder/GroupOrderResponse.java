package org.example.datn.DTO.response.grouporder;

import lombok.*;
import org.example.datn.domain.enums.GroupOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderResponse {
    private Long groupOrderId;
    private Long hostId;
    private String hostName;
    private Long restaurantId;
    private String restaurantName;
    private String restaurantImageUrl;

    private String inviteCode;
    private String inviteUrl;

    private GroupOrderStatus status;
    private String deliveryAddress;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;
    private LocalDateTime joinDeadline;
    private LocalDateTime lockedAt;
    private String note;
    private LocalDateTime createdAt;

    private Integer memberCount;
    private Integer totalItemCount;
    private BigDecimal subtotalAmount; // tổng tạm tính toàn phiên (chưa ship/voucher)

    private Long orderId; // set sau khi checkout thành công

    private List<GroupOrderMemberResponse> members;
}
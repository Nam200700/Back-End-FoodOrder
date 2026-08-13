package org.example.datn.DTO.response.grouporder;

import lombok.*;
import org.example.datn.domain.enums.GroupOrderMemberStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderMemberResponse {
    private Long memberId;
    private Long userId;
    private String fullName;
    private String avatar;
    private Boolean isHost;
    private GroupOrderMemberStatus status;
    private LocalDateTime joinedAt;
    private List<GroupOrderItemResponse> items;
    private BigDecimal memberSubtotal; // tổng tiền món của riêng thành viên này — phục vụ chia tiền
}

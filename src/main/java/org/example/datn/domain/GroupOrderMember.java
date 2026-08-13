package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.GroupOrderMemberStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_order_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_order_member", columnNames = {"group_order_id", "user_id"}),
        indexes = {
                @Index(name = "idx_group_order_members_group", columnList = "group_order_id"),
                @Index(name = "idx_group_order_members_user", columnList = "user_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderMember extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_order_id", nullable = false)
    private GroupOrder groupOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isHost = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupOrderMemberStatus status = GroupOrderMemberStatus.JOINED;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;
}

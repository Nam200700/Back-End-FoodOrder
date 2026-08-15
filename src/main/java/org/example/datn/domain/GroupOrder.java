package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.GroupOrderStatus;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "group_orders", indexes = {
        @Index(name = "idx_group_orders_host", columnList = "host_id"),
        @Index(name = "idx_group_orders_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_group_orders_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, unique = true, length = 36)
    private String inviteCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupOrderStatus status = GroupOrderStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private CustomerAddress address;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal deliveryLat;

    @Column(precision = 10, scale = 7)
    private BigDecimal deliveryLng;

    private LocalDateTime joinDeadline;

    private LocalDateTime lockedAt;

    @Column(length = 255)
    private String note;

    @Builder.Default
    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    private List<GroupOrderMember> members = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "groupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    private List<GroupOrderItem> items = new ArrayList<>();

}

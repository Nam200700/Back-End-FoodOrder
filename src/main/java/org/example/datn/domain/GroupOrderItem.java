package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "group_order_items", indexes = {
        @Index(name = "idx_group_order_items_group", columnList = "group_order_id"),
        @Index(name = "idx_group_order_items_member", columnList = "member_id"),
        @Index(name = "idx_group_order_items_food", columnList = "food_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_order_id", nullable = false)
    private GroupOrder groupOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private GroupOrderMember member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtAdd;

    @Column(length = 255)
    private String note;

}

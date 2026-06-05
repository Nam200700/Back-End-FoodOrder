package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id")
    private Food food;

    /** Snapshot of the food name at order time. */
    @Column(nullable = false, length = 150)
    private String foodName;

    @Column(nullable = false)
    private Integer quantity;

    /** Snapshot of the unit price at order time. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(length = 255)
    private String note;
}

package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.RegisterStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "restaurant_registers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantRegister extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long registerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String restaurantName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 255)
    private String imageUrl;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterStatus status = RegisterStatus.PENDING;

    @Column(length = 500)
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;
}

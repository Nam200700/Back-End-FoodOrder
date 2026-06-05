package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shippers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shippers_user", columnNames = {"user_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipper extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipper_id")
    private Long shipperId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "id_card", nullable = false, length = 20)
    private String idCard;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Builder.Default
    @Column(name = "is_online", nullable = false)
    private Boolean isOnline = false;

    private LocalDateTime lastOnlineAt;

    @Builder.Default
    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_delivery", nullable = false)
    private Integer totalDelivery = 0;

    @Builder.Default
    @Column(name = "active_delivery", nullable = false)
    private Integer activeDelivery = 0;
}

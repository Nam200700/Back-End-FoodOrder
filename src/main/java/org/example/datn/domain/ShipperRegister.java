package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.VehicleType;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_registers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipperRegister extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long registerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String idCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 20)
    private String licensePlate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterStatus status = RegisterStatus.PENDING;

    @Column(length = 300)
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;
}

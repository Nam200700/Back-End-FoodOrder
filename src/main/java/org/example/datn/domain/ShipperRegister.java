package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.VehicleType;
import org.example.datn.util.ShipperIdentityNormalizer;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_registers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipper_registers_id_card", columnNames = {"id_card"}),
        @UniqueConstraint(name = "uk_shipper_registers_plate_norm", columnNames = {"license_plate_norm"})
})
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

    /** Đã chuẩn hoá còn chữ số; NULL khi chưa cung cấp (UNIQUE cho phép nhiều NULL). */
    @Column(name = "id_card", length = 20)
    private String idCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleType vehicleType;

    /** Dạng hiển thị cho người dùng, ví dụ "59H1-234.56". */
    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    /** Bản chuẩn hoá của biển số ("59H123456") — chỉ dùng để so trùng & làm khoá UNIQUE. */
    @Column(name = "license_plate_norm", length = 20)
    private String licensePlateNorm;

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

    /** Chuẩn hoá ngay trước khi ghi — xem giải thích ở {@link Shipper}. */
    @PrePersist
    @PreUpdate
    private void normalizeIdentity() {
        this.idCard = ShipperIdentityNormalizer.normalizeIdCard(this.idCard);
        this.licensePlate = ShipperIdentityNormalizer.blankToNull(this.licensePlate);
        this.licensePlateNorm = ShipperIdentityNormalizer.normalizeLicensePlate(this.licensePlate);
    }
}

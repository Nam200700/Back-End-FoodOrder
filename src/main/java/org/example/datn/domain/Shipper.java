package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.VehicleType;
import org.example.datn.util.ShipperIdentityNormalizer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shippers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shippers_user", columnNames = {"user_id"}),
        @UniqueConstraint(name = "uk_shippers_id_card", columnNames = {"id_card"}),
        @UniqueConstraint(name = "uk_shippers_plate_norm", columnNames = {"license_plate_norm"})
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

    /** Đã chuẩn hoá còn chữ số; NULL khi chưa cung cấp (UNIQUE cho phép nhiều NULL). */
    @Column(name = "id_card", length = 20)
    private String idCard;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    /** Dạng hiển thị cho người dùng, ví dụ "59H1-234.56". */
    @Column(name = "license_plate", length = 20)
    private String licensePlate;

    /** Bản chuẩn hoá của biển số ("59H123456") — chỉ dùng để so trùng & làm khoá UNIQUE. */
    @Column(name = "license_plate_norm", length = 20)
    private String licensePlateNorm;

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

    /** Số đơn shipper đã BỎ (để tính tỷ lệ hủy trên hồ sơ). */
    @Builder.Default
    @Column(name = "cancel_count", nullable = false)
    private Integer cancelCount = 0;

    /**
     * Chuẩn hoá ngay trước khi ghi xuống DB, bất kể lối vào nào (đăng ký, admin duyệt,
     * cập nhật hồ sơ). Đặt ở entity để không phụ thuộc việc từng service có nhớ gọi hay không.
     */
    @PrePersist
    @PreUpdate
    private void normalizeIdentity() {
        this.idCard = ShipperIdentityNormalizer.normalizeIdCard(this.idCard);
        this.licensePlate = ShipperIdentityNormalizer.blankToNull(this.licensePlate);
        this.licensePlateNorm = ShipperIdentityNormalizer.normalizeLicensePlate(this.licensePlate);
    }
}

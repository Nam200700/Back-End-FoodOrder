package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.DiscountType;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false)
    private VoucherIssueType issueType;

    @Builder.Default
    @OneToMany(mappedBy = "voucher")
    private List<UserVoucher> userVouchers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "voucher")
    private List<Order> orders = new ArrayList<>();
}
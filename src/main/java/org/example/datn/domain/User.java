package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.Role;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, length = 15)
    private String phone;

    @Column(unique = true, length = 100)
    private String email;

    /** Null for Google-login accounts. */
    @Column(length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 255)
    private String avatar;

    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    @Builder.Default
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<CustomerAddress> addresses = new java.util.ArrayList<>();

    private java.time.LocalDateTime deletedAt;

    private Long deletedBy;

    private java.time.LocalDateTime lockedAt;

    @Column(length = 255)
    private String lockedReason;

    @Builder.Default
    @Column(nullable = false)
    private Boolean status = true;

    /** Điểm uy tín 0..100 (khởi tạo 100). Trừ khi gây hủy đơn có lỗi, cộng khi hoàn tất đơn. */
    @Builder.Default
    @Column(name = "reputation_score", nullable = false)
    private Integer reputationScore = 100;

    /** Điểm thưởng tích luỹ (loyalty) để đổi voucher. */
    @Builder.Default
    @Column(name = "loyalty_points", nullable = false)
    private Integer loyaltyPoints = 0;

    /** Dành cho xác thực mã otp check khi tk ko xác thực hoặc bị fail */
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @OneToMany(mappedBy = "user")
    private List<UserVoucher> userVouchers = new ArrayList<>();
}

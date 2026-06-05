package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.OtpPurpose;

import java.time.LocalDateTime;

@Entity
@Table(name = "otps", indexes = {
        @Index(name = "idx_otps_phone_purpose", columnList = "phone, purpose")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Otp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otpId;

    @Column(nullable = false, length = 100)
    private String phone;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer failCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isUsed = false;
}

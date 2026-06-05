package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String restaurantName;

    @Column(length = 255)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 15)
    private String phone;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status_detail", nullable = false, length = 20)
    private org.example.datn.domain.enums.StatusDetail statusDetail = org.example.datn.domain.enums.StatusDetail.ACTIVE;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "opens_at")
    private java.time.LocalTime opensAt;

    @Column(name = "closes_at")
    private java.time.LocalTime closesAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean status = true;
}

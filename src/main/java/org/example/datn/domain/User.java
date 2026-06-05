package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.Role;

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
}

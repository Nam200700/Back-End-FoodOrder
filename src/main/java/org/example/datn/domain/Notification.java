package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;
import org.example.datn.domain.enums.NotificationType;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String body;

    /** Id of the related entity (e.g. orderId). */
    private Long refId;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 20)
    private org.example.datn.domain.enums.Role recipientRole;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isRead = false;
}

package org.example.datn.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.datn.domain.base.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversation_pair", columnNames = {"user1_id", "user2_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    private LocalDateTime lastMessageAt;

    @Builder.Default
    @Column(name = "unread_count_user1", nullable = false)
    private Integer unreadCountUser1 = 0;

    @Builder.Default
    @Column(name = "unread_count_user2", nullable = false)
    private Integer unreadCountUser2 = 0;
}

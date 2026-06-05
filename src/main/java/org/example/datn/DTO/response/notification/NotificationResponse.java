package org.example.datn.DTO.response.notification;

import lombok.Builder;
import lombok.Data;
import org.example.datn.domain.enums.NotificationType;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long notificationId;
    private NotificationType type;
    private String title;
    private String body;
    private Long refId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

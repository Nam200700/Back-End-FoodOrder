package org.example.datn.DTO.event;

import org.example.datn.domain.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * Personal notification pushed to {@code /user/{userId}/queue/notify}.
 */
public record NotificationEvent(
        NotificationType type,
        String title,
        String body,
        Long refId,
        LocalDateTime timestamp) {
}

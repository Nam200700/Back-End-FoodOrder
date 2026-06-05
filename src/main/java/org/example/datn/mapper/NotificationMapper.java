package org.example.datn.mapper;

import org.example.datn.domain.Notification;
import org.example.datn.DTO.event.NotificationEvent;
import org.example.datn.DTO.response.notification.NotificationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    default NotificationEvent toEvent(Notification n) {
        return new NotificationEvent(n.getType(), n.getTitle(), n.getBody(), n.getRefId(), n.getCreatedAt());
    }
}

package org.example.datn.mapper;

import org.example.datn.domain.Message;
import org.example.datn.DTO.response.chat.MessageResponse;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }
        return MessageResponse.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversation() != null ? message.getConversation().getConversationId() : null)
                .senderId(message.getSender() != null ? message.getSender().getUserId() : null)
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

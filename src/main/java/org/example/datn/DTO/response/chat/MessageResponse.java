package org.example.datn.DTO.response.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

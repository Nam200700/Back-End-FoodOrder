package org.example.datn.DTO.response.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {
    private Long conversationId;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserAvatar;
    private LocalDateTime lastMessageAt;
}

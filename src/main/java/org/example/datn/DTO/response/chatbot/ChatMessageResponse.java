package org.example.datn.DTO.response.chatbot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.datn.domain.enums.Sender;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private Sender sender;
    private String content;
    private LocalDateTime createdAt;
}

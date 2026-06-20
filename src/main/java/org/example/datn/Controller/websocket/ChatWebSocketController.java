package org.example.datn.Controller.websocket;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.chat.SendMessageRequest;
import org.example.datn.DTO.response.chat.MessageResponse;
import org.example.datn.Service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Client publishes to {@code /app/chat/{convId}}; the server persists the
     * message and re-broadcasts it to {@code /topic/chat/{convId}}.
     */
    @MessageMapping("/chat/{convId}")
    public void sendMessage(@DestinationVariable Long convId,
                            @Payload SendMessageRequest req,
                            Principal principal) {
        if (principal == null) {
            return; // unauthenticated STOMP session
        }
        Long senderId = Long.parseLong(principal.getName());
        MessageResponse message = chatService.saveMessage(convId, senderId, req.getContent());
        messagingTemplate.convertAndSend("/topic/chat/" + convId, message);
    }
}

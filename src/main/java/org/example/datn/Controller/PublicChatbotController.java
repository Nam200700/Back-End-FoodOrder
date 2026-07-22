package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.chatbot.ChatbotRequest;
import org.example.datn.DTO.response.chatbot.ChatbotResponse;
import org.example.datn.DTO.response.chatbot.ChatMessageResponse;
import org.example.datn.common.ApiResponse;
import org.example.datn.Service.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/chatbot")
@RequiredArgsConstructor
public class PublicChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(@RequestBody ChatbotRequest req) {
        Long guestId = chatbotService.resolveGuestUserId();
        String reply = chatbotService.chatWithAI(guestId, req.getMessage());
        return ResponseEntity.ok(ApiResponse.ok(new ChatbotResponse(reply)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getHistory() {
        Long guestId = chatbotService.resolveGuestUserId();
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getChatHistory(guestId)));
    }
}

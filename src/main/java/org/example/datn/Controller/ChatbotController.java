package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.chatbot.ChatbotRequest;
import org.example.datn.DTO.response.chatbot.ChatbotResponse;
import org.example.datn.DTO.response.chatbot.ChatMessageResponse;
import org.example.datn.common.ApiResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chatbot")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatbotResponse>> chat(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ChatbotRequest req) {
        String reply = chatbotService.chatWithAI(user.getUserId(), req.getMessage());
        return ResponseEntity.ok(ApiResponse.ok(new ChatbotResponse(reply)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getHistory(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getChatHistory(user.getUserId())));
    }
}

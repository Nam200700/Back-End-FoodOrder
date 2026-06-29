package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.DTO.response.chat.ConversationResponse;
import org.example.datn.DTO.response.chat.MessageResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.ChatService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> myConversations(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.listMyConversations(user.getUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> openConversation(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long otherUserId) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getOrCreateConversation(user.getUserId(), otherUserId)));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> messages(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long conversationId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getMessages(user.getUserId(), conversationId, pageable)));
    }
}

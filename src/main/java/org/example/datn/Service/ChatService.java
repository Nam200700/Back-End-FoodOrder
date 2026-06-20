package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Conversation;
import org.example.datn.domain.Message;
import org.example.datn.domain.User;
import org.example.datn.DTO.response.chat.ConversationResponse;
import org.example.datn.DTO.response.chat.MessageResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.MessageMapper;
import org.example.datn.Repository.ConversationRepository;
import org.example.datn.Repository.MessageRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.security.OwnershipGuard;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final OwnershipGuard ownershipGuard;

    @Transactional
    public ConversationResponse getOrCreateConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không thể tự trò chuyện với chính mình");
        }
        if (!userRepository.existsById(otherUserId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        Conversation conversation = conversationRepository.findBetween(userId, otherUserId)
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .user1(userRepository.getReferenceById(userId))
                        .user2(userRepository.getReferenceById(otherUserId))
                        .build()));
        return toConversationResponse(conversation, userId);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listMyConversations(Long userId) {
        return conversationRepository.findAllForUser(userId).stream()
                .map(c -> toConversationResponse(c, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(Long userId, Long conversationId, Pageable pageable) {
        Conversation conversation = conversationRepository.findByIdOrThrow(conversationId, ErrorCode.NOT_FOUND);
        ownershipGuard.checkConversationMember(conversation, userId);
        return PageResponse.from(messageRepository
                .findByConversationConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(messageMapper::toResponse));
    }

    /** Persists a message (called by the STOMP handler) and returns it for broadcast. */
    @Transactional
    public MessageResponse saveMessage(Long conversationId, Long senderId, String content) {
        Conversation conversation = conversationRepository.findByIdOrThrow(conversationId, ErrorCode.NOT_FOUND);
        ownershipGuard.checkConversationMember(conversation, senderId);

        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(userRepository.getReferenceById(senderId))
                .content(content)
                .isRead(false)
                .build());

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        return messageMapper.toResponse(message);
    }

    private ConversationResponse toConversationResponse(Conversation conversation, Long userId) {
        User other = conversation.getUser1().getUserId().equals(userId)
                ? conversation.getUser2()
                : conversation.getUser1();
        return ConversationResponse.builder()
                .conversationId(conversation.getConversationId())
                .otherUserId(other.getUserId())
                .otherUserName(other.getFullName())
                .otherUserAvatar(other.getAvatar())
                .lastMessageAt(conversation.getLastMessageAt())
                .build();
    }
}

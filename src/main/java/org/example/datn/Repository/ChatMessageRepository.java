package org.example.datn.Repository;

import org.example.datn.domain.ChatMessage;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends BaseRepository<ChatMessage, Long> {

    List<ChatMessage> findTop5ByUserUserIdOrderByCreatedAtDesc(Long userId);

    List<ChatMessage> findTop50ByUserUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserUserIdAndSenderAndCreatedAtAfter(Long userId, org.example.datn.domain.enums.Sender sender, LocalDateTime time);
}

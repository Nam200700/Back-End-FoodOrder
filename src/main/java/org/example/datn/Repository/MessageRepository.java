package org.example.datn.Repository;

import org.example.datn.domain.Message;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends BaseRepository<Message, Long> {

    Page<Message> findByConversationConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
}

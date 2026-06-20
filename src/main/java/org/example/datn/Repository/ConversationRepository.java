package org.example.datn.Repository;

import org.example.datn.domain.Conversation;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends BaseRepository<Conversation, Long> {

    @Query("""
            SELECT c FROM Conversation c
            WHERE (c.user1.userId = :a AND c.user2.userId = :b)
               OR (c.user1.userId = :b AND c.user2.userId = :a)
            """)
    Optional<Conversation> findBetween(@Param("a") Long userA, @Param("b") Long userB);

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.user1.userId = :userId OR c.user2.userId = :userId
            ORDER BY c.lastMessageAt DESC NULLS LAST
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}

package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.GroupOrder;
import org.example.datn.domain.enums.GroupOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupOrderRepository extends BaseRepository<GroupOrder, Long> {

    boolean existsByInviteCode(String inviteCode);

    Optional<GroupOrder> findByInviteCode(String inviteCode);

    /** Fetch-join members + items + food để hiển thị chi tiết phiên, tránh N+1. */
    @Query("""
            SELECT DISTINCT g FROM GroupOrder g
            LEFT JOIN FETCH g.members m
            LEFT JOIN FETCH m.user
            LEFT JOIN FETCH g.items i
            LEFT JOIN FETCH i.food
            WHERE g.groupOrderId = :id
            """)
    Optional<GroupOrder> findDetailById(@Param("id") Long id);

    Page<GroupOrder> findByHostUserIdOrderByCreatedAtDesc(Long hostId, Pageable pageable);

    @Query("""
            SELECT DISTINCT g FROM GroupOrder g
            JOIN g.members m
            WHERE m.user.userId = :userId
            ORDER BY g.createdAt DESC
            """)
    Page<GroupOrder> findByMemberUserId(@Param("userId") Long userId, Pageable pageable);

    List<GroupOrder> findByStatusAndJoinDeadlineBefore(GroupOrderStatus status, LocalDateTime cutoff);
}

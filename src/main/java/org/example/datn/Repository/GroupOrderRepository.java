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

    @Query("SELECT g FROM GroupOrder g WHERE g.groupOrderId = :groupOrderId")
    Optional<GroupOrder> findDetailById(@Param("groupOrderId") Long groupOrderId);

    /**
     * FIX MultipleBagFetchException: KHÔNG fetch-join 2 List (members + items) trong cùng 1 câu.
     * Query này chỉ fetch "members" — dùng kèm findWithItems() bên dưới trong cùng transaction,
     * Hibernate Persistence Context sẽ tự gắn cả 2 kết quả vào CÙNG 1 Java object (theo id).
     */
    @Query("""
            SELECT DISTINCT g FROM GroupOrder g
            LEFT JOIN FETCH g.members m
            LEFT JOIN FETCH m.user
            WHERE g.groupOrderId = :id
            """)
    Optional<GroupOrder> findWithMembers(@Param("id") Long id);

    /** Fetch riêng "items" — xem ghi chú ở findWithMembers(). */
    @Query("""
            SELECT DISTINCT g FROM GroupOrder g
            LEFT JOIN FETCH g.items i
            LEFT JOIN FETCH i.food
            LEFT JOIN FETCH i.member
            WHERE g.groupOrderId = :id
            """)
    Optional<GroupOrder> findWithItems(@Param("id") Long id);

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
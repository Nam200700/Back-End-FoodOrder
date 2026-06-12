package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends BaseRepository<Transaction, Long> {

    List<Transaction> findByUserUserId(Long userId);

    List<Transaction> findByOrderOrderId(Long orderId);

    List<Transaction> findByOrderOrderIdAndTypeIn(Long orderId, List<org.example.datn.domain.enums.TransactionType> types);


    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type")
    BigDecimal sumAmountByType(@Param("type") org.example.datn.domain.enums.TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.userId = :userId AND t.type = :type")
    BigDecimal sumAmountByUserIdAndType(@Param("userId") Long userId, @Param("type") org.example.datn.domain.enums.TransactionType type);
}

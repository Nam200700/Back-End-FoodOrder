package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Voucher;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends BaseRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);
    long countByStatus(VoucherStatus status);
    long countByEndDateBefore(LocalDateTime now);

    List<Voucher> findByIssueTypeAndStatusAndEndDateAfter(VoucherIssueType issueType, VoucherStatus status, LocalDateTime now);

    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(v.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:status IS NULL OR " +
            "     (:status = 'EXPIRED' AND v.endDate < :now) OR " +
            "     (:status != 'EXPIRED' AND v.status = :status)) " +
            "ORDER BY v.createdAt DESC, v.voucherId DESC")
    Page<Voucher> searchAndFilter(@Param("keyword") String keyword,
                                  @Param("status") VoucherStatus status,
                                  @Param("now") LocalDateTime now,
                                  Pageable pageable);

    @Query("SELECT v FROM Voucher v WHERE v.issueType = :issueType AND v.status = 'ACTIVE' AND (v.endDate IS NULL OR v.endDate > CURRENT_TIMESTAMP)")
    Optional<Voucher> findActiveVoucherByIssueType(@Param("issueType") VoucherIssueType issueType);

    // Kiểm tra xem đã có voucher loại issueType đang ACTIVE và chưa hết hạn chưa
    @Query("SELECT COUNT(v) > 0 FROM Voucher v WHERE v.issueType = :issueType AND v.status = 'ACTIVE' AND v.endDate > :now")
    boolean existsActiveVoucherByIssueType(@Param("issueType") VoucherIssueType issueType, @Param("now") LocalDateTime now);

    // Kiểm tra tương tự trừ chính voucher đang edit
    @Query("SELECT COUNT(v) > 0 FROM Voucher v WHERE v.issueType = :issueType AND v.status = 'ACTIVE' AND v.endDate > :now AND v.voucherId != :excludeId")
    boolean existsActiveVoucherByIssueTypeExcludingId(@Param("issueType") VoucherIssueType issueType, @Param("now") LocalDateTime now, @Param("excludeId") Long excludeId);

    @Query("SELECT v FROM Voucher v " +
            "LEFT JOIN v.userVouchers uv ON uv.user.userId = :userId " +
            "WHERE v.issueType = :issueType " +
            "AND v.status = :status " +
            "AND v.endDate > :now " +
            "AND v.startDate <= :now " +
            "AND uv.userVoucherId IS NULL")
    List<Voucher> findUnclaimedPublicVouchersForUser(@Param("issueType") VoucherIssueType issueType,
                                                     @Param("status") VoucherStatus status,
                                                     @Param("now") LocalDateTime now,
                                                     @Param("userId") Long userId);
}
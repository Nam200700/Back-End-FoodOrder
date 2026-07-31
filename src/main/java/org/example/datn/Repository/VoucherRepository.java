package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Voucher;
import org.example.datn.domain.enums.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends BaseRepository<Voucher, Long> {
    Optional<Voucher> findByCode(String code);
    boolean existsByCode(String code);
    long countByStatus(VoucherStatus status);

    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(v.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:status IS NULL OR v.status = :status)")
    Page<Voucher> searchAndFilter(@Param("keyword") String keyword,
                                  @Param("status") VoucherStatus status,
                                  Pageable pageable);
}

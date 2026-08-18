package org.example.datn.Repository;

import jakarta.persistence.LockModeType;
import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.UserVoucher;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserVoucherRepository extends BaseRepository<UserVoucher, Long>  {

    // Lấy danh sách voucher trong ví của user
    List<UserVoucher> findByUser_UserIdAndUsed(Long userId, Boolean used);

    // Kiểm tra xem user đã lưu voucher này chưa
    boolean existsByUser_UserIdAndVoucher_VoucherId(Long userId, Long voucherId);

    Optional<UserVoucher> findByUserUserIdAndVoucherVoucherId(Long userId, Long voucherId);

    @Query("SELECT uv FROM UserVoucher uv WHERE uv.user.userId = :userId AND uv.used = :used AND uv.expiredAt > :currentDate")
    List<UserVoucher> findValidUserVouchers(@Param("userId") Long userId,
                                            @Param("used") Boolean used,
                                            @Param("currentDate") LocalDateTime currentDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uv FROM UserVoucher uv WHERE uv.userVoucherId = :id")
    Optional<UserVoucher> findByIdForUpdate(@Param("id") Long id);

}

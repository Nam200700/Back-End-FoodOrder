package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.UserVoucher;

import java.util.List;

public interface UserVoucherRepository extends BaseRepository<UserVoucher, Long>  {

    // Lấy danh sách voucher trong ví của user
    List<UserVoucher> findByUser_UserIdAndUsed(Long userId, Boolean used);

    // Kiểm tra xem user đã lưu voucher này chưa
    boolean existsByUser_UserIdAndVoucher_VoucherId(Long userId, Long voucherId);

}

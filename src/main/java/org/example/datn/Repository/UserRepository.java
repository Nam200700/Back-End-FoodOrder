package org.example.datn.Repository;

import org.example.datn.domain.User;
import org.example.datn.domain.enums.Role;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    // Thống kê dashboard admin: đếm tài khoản bị khoá + đếm theo vai trò bỏ hardcode đi
    long countByStatusFalse();

    long countByRole(Role role);

    // Lọc theo cả Role và Trạng thái Active/Blocked
    Page<User> findByRoleAndStatus(Role role, Boolean status, Pageable pageable);

    // Chỉ lọc theo Role (lấy tất cả trạng thái)
    Page<User> findByRole(Role role, Pageable pageable);

    // Chỉ lọc theo Trạng thái Active/Blocked (lấy tất cả role)
    Page<User> findByStatus(Boolean status, Pageable pageable);
    
}

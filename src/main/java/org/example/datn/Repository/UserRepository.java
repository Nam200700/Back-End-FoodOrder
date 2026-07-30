package org.example.datn.Repository;

import org.example.datn.domain.User;
import org.example.datn.domain.enums.Role;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    long countByStatusTrue();
    long countByRoleAndStatus(Role role, Boolean status);

    // Tăng trưởng thành viên cho dashboard admin (theo created_at, không cap)
    long countByCreatedAtAfter(java.time.LocalDateTime since);
    long countByRoleAndCreatedAtAfter(Role role, java.time.LocalDateTime since);

    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("role") Role role,
            @Param("status") Boolean status,
            Pageable pageable
            );
}

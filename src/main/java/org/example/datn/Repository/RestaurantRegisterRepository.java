package org.example.datn.Repository;

import org.example.datn.domain.RestaurantRegister;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantRegisterRepository extends BaseRepository<RestaurantRegister, Long> {
    Page<RestaurantRegister> findByStatus(RegisterStatus status, Pageable pageable);
    java.util.Optional<RestaurantRegister> findTopByOwnerUserIdOrderByRegisterIdDesc(Long ownerId);

    // Đếm hồ sơ quán đang chờ duyệt cho badge/alert dashboard admin
    long countByStatus(RegisterStatus status);

    // Chống trùng số điện thoại quán ngay từ khâu đăng ký đối tác
    boolean existsByPhone(String phone);

    // Như trên nhưng BỎ QUA hồ sơ của chính chủ quán này, để họ không bị hồ sơ cũ của mình chặn
    boolean existsByPhoneAndOwner_UserIdNot(String phone, Long ownerId);
}

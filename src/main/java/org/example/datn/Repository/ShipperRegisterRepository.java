package org.example.datn.Repository;

import org.example.datn.domain.ShipperRegister;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ShipperRegisterRepository extends BaseRepository<ShipperRegister, Long> {
    Page<ShipperRegister> findByStatus(RegisterStatus status, Pageable pageable);
    // Đếm hồ sơ shipper đang chờ duyệt cho badge/alert dashboard admin
    long countByStatus(RegisterStatus status);
    Optional<ShipperRegister> findByUserUserId(Long userId);

    /** Lấy hồ sơ shipper của nhiều user cùng lúc (1 câu IN) — khử N+1 khi enrich danh sách đơn. */
    java.util.List<ShipperRegister> findByUserUserIdIn(java.util.Collection<Long> userIds);
    Optional<ShipperRegister> findTopByUserUserIdOrderByRegisterIdDesc(Long userId);
    boolean existsByIdCard(String idCard);
    boolean existsByLicensePlate(String licensePlate);
}

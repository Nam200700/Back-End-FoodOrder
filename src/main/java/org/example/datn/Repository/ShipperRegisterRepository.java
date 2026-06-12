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
    Optional<ShipperRegister> findByUserUserId(Long userId);
    Optional<ShipperRegister> findTopByUserUserIdOrderByRegisterIdDesc(Long userId);
}

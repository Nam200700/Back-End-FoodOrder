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
}

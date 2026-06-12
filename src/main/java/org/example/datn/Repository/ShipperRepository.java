package org.example.datn.Repository;

import org.example.datn.domain.Shipper;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipperRepository extends BaseRepository<Shipper, Long> {

    Optional<Shipper> findByUserUserId(Long userId);
}

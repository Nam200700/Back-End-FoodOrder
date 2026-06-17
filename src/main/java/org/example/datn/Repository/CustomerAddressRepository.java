package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.CustomerAddress;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAddressRepository extends BaseRepository<CustomerAddress, Long> {
}

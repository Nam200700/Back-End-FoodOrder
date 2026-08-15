package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.GroupOrderMember;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupOrderMemberRepository extends BaseRepository<GroupOrderMember, Long> {

    Optional<GroupOrderMember> findByGroupOrderGroupOrderIdAndUserUserId(Long groupOrderId, Long userId);

    List<GroupOrderMember> findByGroupOrderGroupOrderId(Long groupOrderId);

    long countByGroupOrderGroupOrderId(Long groupOrderId);
}

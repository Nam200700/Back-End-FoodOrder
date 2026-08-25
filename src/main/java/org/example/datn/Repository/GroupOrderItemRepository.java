package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.GroupOrderItem;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupOrderItemRepository extends BaseRepository<GroupOrderItem, Long> {

    List<GroupOrderItem> findByGroupOrderGroupOrderId(Long groupOrderId);

    List<GroupOrderItem> findByMemberMemberId(Long memberId);

    Optional<GroupOrderItem> findByGroupOrderItemIdAndMemberMemberId(Long itemId, Long memberId);

    void deleteByMemberMemberId(Long memberId);

    Optional<GroupOrderItem> findByGroupOrderItemIdAndGroupOrderGroupOrderId(Long groupOrderItemId, Long groupOrderId);
}
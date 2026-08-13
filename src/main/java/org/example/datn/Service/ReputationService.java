package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Repository.UserRepository;
import org.example.datn.domain.User;
import org.springframework.stereotype.Service;

/**
 * Điểm uy tín (reputation) 0..100 áp cho mọi user. Ai gây hủy đơn có lỗi thì bị trừ;
 * hoàn tất đơn thì cộng dần hồi phục. Các mốc chặn hành vi (đặt COD / nhận đơn) khi điểm thấp.
 * Gọi trong cùng transaction của OrderService (entity đang được quản lý → dirty-check tự lưu,
 * vẫn save tường minh cho chắc).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    public static final int START = 100;
    public static final int MIN = 0;
    public static final int MAX = 100;

    // ─── Ngưỡng chặn hành vi ───
    /** Khách < ngưỡng này → không đặt được đơn (chống bom hàng COD). */
    public static final int CUSTOMER_ORDER_BLOCK_BELOW = 30;
    /** Shipper < ngưỡng này → không nhận đơn mới. */
    public static final int SHIPPER_ACCEPT_BLOCK_BELOW = 50;

    // ─── Mức trừ/cộng điểm ───
    public static final int PENALTY_CUSTOMER_LATE_CANCEL = 5;   // khách hủy sau khi quán đã xác nhận
    public static final int PENALTY_OWNER_REJECT = 3;           // quán từ chối đơn mới (PENDING)
    public static final int PENALTY_OWNER_CANCEL = 5;           // quán hủy sau khi đã xác nhận
    public static final int PENALTY_SHIPPER_ABANDON = 10;       // shipper bỏ đơn
    public static final int REWARD_ON_COMPLETE = 1;             // hoàn tất đơn → hồi điểm

    private final UserRepository userRepository;

    /** Trừ điểm (amount > 0), kẹp [MIN, MAX]. */
    public void penalize(User user, int amount) {
        adjust(user, -Math.abs(amount));
    }

    /** Cộng điểm (amount > 0), kẹp [MIN, MAX]. */
    public void reward(User user, int amount) {
        adjust(user, Math.abs(amount));
    }

    private void adjust(User user, int delta) {
        if (user == null) return;
        int cur = user.getReputationScore() != null ? user.getReputationScore() : START;
        int next = Math.max(MIN, Math.min(MAX, cur + delta));
        if (next != cur) {
            user.setReputationScore(next);
            userRepository.save(user);
            log.debug("Reputation user {} : {} -> {} (delta {})", user.getUserId(), cur, next, delta);
        }
    }
}

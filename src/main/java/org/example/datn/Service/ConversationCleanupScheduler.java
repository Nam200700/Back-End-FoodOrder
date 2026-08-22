package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Repository.ConversationRepository;
import org.example.datn.util.DateTimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Dọn định kỳ các cuộc trò chuyện "nguội" (không có tin nhắn mới quá 3 ngày).
 * Thay cho MySQL EVENT trong migration cũ — chạy hoàn toàn ở tầng ứng dụng nên
 * KHÔNG cần quyền EVENT / event_scheduler của DB (an toàn khi deploy host chia sẻ).
 * Messages con tự xoá theo nhờ ON DELETE CASCADE ở FK phía DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationCleanupScheduler {

    private static final int STALE_DAYS = 3;

    private final ConversationRepository conversationRepository;

    // 03:00 mỗi ngày (giờ máy chủ) — khung giờ vắng để tránh ảnh hưởng người dùng.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupStaleConversations() {
        LocalDateTime cutoff = DateTimeUtils.now().minusDays(STALE_DAYS);
        int removed = conversationRepository.deleteStale(cutoff);
        if (removed > 0) {
            log.info("Đã dọn {} cuộc trò chuyện cũ (> {} ngày không hoạt động).", removed, STALE_DAYS);
        }
    }
}

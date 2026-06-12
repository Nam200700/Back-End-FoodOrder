package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.DTO.event.NotificationEvent;
import org.example.datn.DTO.response.notification.NotificationResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.NotificationRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Notification;
import org.example.datn.domain.Order;
import org.example.datn.domain.User;
import org.example.datn.domain.enums.NotificationType;
import org.example.datn.domain.enums.Role;
import org.example.datn.mapper.NotificationMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a notification then pushes it over WebSocket. Callers must resolve
 * the target {@code userId} inside their own transaction (entity associations
 * are lazy and would not survive the async hop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("notificationExecutor")
    @Transactional
    public void notifyUser(Long userId, NotificationType type, Long refId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Notification notif = Notification.builder()
                .user(user)
                .type(type)
                .title(title(type))
                .body(body(type, refId))
                .refId(refId)
                .recipientRole(user.getRole())
                .isRead(false)
                .build();
        notif = notificationRepository.save(notif);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), "/queue/notify", notificationMapper.toEvent(notif));
        log.debug("Notified user {} with {}", userId, type);
    }

    @Transactional
    public void notifyOrderCancelled(Order order, Role canceller) {
        Long oid = order.getOrderId();
        String title = "Đơn #" + oid + " đã hủy";

        // Gửi cho KHÁCH (nếu không phải chính khách hủy)
        if (canceller != Role.CUSTOMER) {
            create(order.getCustomer().getUserId(), NotificationType.ORDER_CANCELLED,
                   Role.CUSTOMER, title, "Đơn hàng của bạn đã được hủy bởi " + 
                   (canceller == Role.OWNER ? "quán ăn" : "quản trị viên"), 
                   "/orders/" + oid, oid);
        }

        // Gửi cho QUÁN (nếu không phải chính quán hủy)
        if (canceller != Role.OWNER) {
            create(order.getRestaurant().getOwner().getUserId(), NotificationType.ORDER_CANCELLED,
                   Role.OWNER, title, "Một đơn hàng đã bị hủy bởi " +
                   (canceller == Role.CUSTOMER ? "khách hàng" : "quản trị viên"),
                   "/owner/orders/" + oid, oid);
        }

        // Gửi cho SHIPPER nếu đã gán (thường khi Admin hủy đơn giai đoạn sau)
        if (order.getShipper() != null && canceller == Role.ADMIN) {
            create(order.getShipper().getUserId(), NotificationType.ORDER_CANCELLED,
                   Role.SHIPPER, title, "Đơn bạn đang giao đã bị hủy bởi quản trị viên", 
                   "/shipper/orders/" + oid, oid);
        }
    }

    private void create(Long userId, NotificationType type, Role role, 
                        String title, String body, String actionUrl, Long refId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        
        Notification notif = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .refId(refId)
                .actionUrl(actionUrl)
                .recipientRole(role)
                .isRead(false)
                .build();
        notif = notificationRepository.save(notif);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), "/queue/notify", notificationMapper.toEvent(notif));
        log.debug("Created cancel notification for user {} with type {}", userId, type);
    }

    /** Broadcast to all shippers watching the available-orders feed (not persisted). */
    @Async("notificationExecutor")
    public void broadcastToShippers(Long orderId, NotificationType type) {
        messagingTemplate.convertAndSend("/topic/available-orders",
                new NotificationEvent(type, title(type), body(type, orderId), orderId, java.time.LocalDateTime.now()));
    }

    // ─── Read APIs ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(Long userId, Pageable pageable) {
        return PageResponse.from(notificationRepository
                .findByUserUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getUserId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private String title(NotificationType type) {
        return switch (type) {
            case ORDER_NEW -> "Đơn hàng mới";
            case ORDER_CONFIRMED -> "Đơn đã được xác nhận";
            case ORDER_PREPARING -> "Quán đang chuẩn bị món";
            case ORDER_READY_PICKUP -> "Đơn sẵn sàng lấy hàng";
            case SHIPPER_ASSIGNED -> "Shipper đã nhận đơn";
            case ORDER_COMPLETED -> "Đơn đã hoàn thành";
            case ORDER_CANCELLED -> "Đơn đã bị hủy";
        };
    }

    private String body(NotificationType type, Long orderId) {
        return "Đơn hàng #" + orderId + ": " + title(type);
    }
}

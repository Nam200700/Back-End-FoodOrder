package org.example.datn.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Service.GroupOrderService;
import org.example.datn.Service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;
    private final GroupOrderService groupOrderService;

    @Scheduled(fixedRate = 30_000)
    public void autoCancelExpiredPendingOrders() {
        try {
            orderService.autoCancelExpiredPendingOrders();
        } catch (Exception e) {
            log.error("Lỗi khi chạy job tự động hủy đơn hàng quá hạn PENDING", e);
        }
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void expireOverdueGroupOrders() {
        try {
            groupOrderService.expireOverdueGroupOrders();
        } catch (Exception e) {
            log.error("Lỗi khi chạy job expire phiên đặt nhóm quá hạn", e);
        }
    }
}
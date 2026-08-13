package org.example.datn.DTO.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payload NHẸ cho việc theo dõi đơn ở trang quản lý đơn của quán:
 *  - counts: số đơn theo trạng thái (key = OrderStatus.name()) + "ALL" tổng.
 *  - pending: danh sách đơn CHỜ XÁC NHẬN rút gọn (đủ để báo "đơn mới": id + tên khách + tổng tiền).
 *
 * Thay cho việc FE tải cả nghìn đơn đã enrich chỉ để đếm + dò đơn mới.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOrderMonitorResponse {

    private Map<String, Long> counts;
    private List<PendingBrief> pending;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingBrief {
        private Long orderId;
        private String customerName;
        private BigDecimal totalAmount;
    }
}

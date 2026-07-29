package org.example.datn.DTO.response.stats;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StatsOverviewResponse {
    private long totalUsers;
    private long totalRestaurants;
    private long totalOrders;
    private long completedOrders;
    private long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalCommission;
    private BigDecimal totalMerchantNet;
    private BigDecimal totalShipperShare;
    private BigDecimal commissionRate;

    // ─── Bổ sung cho dashboard admin bỏ hardcode đi dùng dữ liệu từ db đẩy lên ───
    private long activeShippers;      // shipper đang online
    private long lockedUsers;         // tài khoản bị khoá
    private long customerCount;       // phân bố vai trò: khách hàng
    private long ownerCount;          // phân bố vai trò: chủ quán
    private long shipperCount;        // phân bố vai trò: tài xế
    private long pendingRestaurantRegisters; // hồ sơ quán chờ duyệt
    private long pendingShipperRegisters;    // hồ sơ shipper chờ duyệt
    private long pendingReports;      // báo cáo chưa xử lý
    private BigDecimal avgOrderValue; // giá trị đơn trung bình (AOV = GTV / đơn hoàn tất)
    // Phân bố trạng thái đơn (panel vận hành)
    private long pendingOrders;
    private long preparingOrders;
    private long deliveringOrders;
}

package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.DTO.response.stats.AdminInsightsResponse;
import org.example.datn.DTO.response.stats.AdminReportResponse;
import org.example.datn.DTO.response.stats.MerchantInsightsResponse;
import org.example.datn.DTO.response.stats.MerchantReportResponse;
import org.example.datn.DTO.response.stats.MerchantStatsResponse;
import org.example.datn.DTO.response.stats.StatsOverviewResponse;
import org.example.datn.DTO.response.stats.VoucherAnalyticsResponse;
import org.example.datn.Repository.VoucherRepository;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.VoucherStatus;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.FoodRepository;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.Repository.ReportRepository;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.Repository.ShipperRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.TransactionRepository;
import org.example.datn.security.OwnershipGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.time.DayOfWeek;
import org.example.datn.domain.Order;
import org.example.datn.domain.Shipper;
import org.example.datn.DTO.response.stats.ShipperInsightsResponse;
import org.example.datn.Exception.AppException;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final OwnershipGuard ownershipGuard;
    private final TransactionRepository transactionRepository;
    // Repo bổ sung phục vụ số liệu thật cho dashboard admin + rating merchant
    private final ShipperRepository shipperRepository;
    private final RestaurantRegisterRepository restaurantRegisterRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final FoodRepository foodRepository; // đếm sức khoẻ thực đơn cho dashboard insights
    private final VoucherRepository voucherRepository; // phân tích voucher cho dashboard admin

    @Value("${platform.commission-rate:0.10}")
    private double commissionRate;

    @Transactional(readOnly = true)
    public StatsOverviewResponse adminOverview() {
        long totalUsers = userRepository.count();
        long totalRestaurants = restaurantRepository.count();
        long totalOrders = orderRepository.count();
        long completedOrders = orderRepository.countByOrderStatus(OrderStatus.COMPLETED);
        long cancelledOrders = orderRepository.countByOrderStatus(OrderStatus.CANCELLED);

        // Tính GTV (doanh số đơn hoàn tất không gồm refund)
        BigDecimal totalRevenue = orderRepository.sumCompletedRevenueExcludeRefunded();
        BigDecimal totalSubtotal = orderRepository.sumCompletedSubtotalExcludeRefunded();

        // Tính toán các khoản thực nhận từ transactions
        BigDecimal totalMerchantNet = transactionRepository.sumAmountByType(
                org.example.datn.domain.enums.TransactionType.MERCHANT_EARNING);
        BigDecimal totalShipperShare = transactionRepository.sumAmountByType(
                org.example.datn.domain.enums.TransactionType.SHIPPER_EARNING);

        // Commission sàn = subtotal * commissionRate
        BigDecimal totalCommission = totalSubtotal.multiply(BigDecimal.valueOf(commissionRate));

        // AOV = GTV / số đơn hoàn tất (tránh chia 0)
        BigDecimal avgOrderValue = completedOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completedOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Số liệu thật cho dashboard admin (thay cho hardcode/lọc-theo-trang ở FE)
        long activeShippers = shipperRepository.countByIsOnlineTrue();
        long lockedUsers = userRepository.countByStatusFalse();
        long customerCount = userRepository.countByRole(Role.CUSTOMER);
        long ownerCount = userRepository.countByRole(Role.OWNER);
        long shipperCount = userRepository.countByRole(Role.SHIPPER);
        long pendingRestaurantRegisters = restaurantRegisterRepository.countByStatus(RegisterStatus.PENDING);
        long pendingShipperRegisters = shipperRegisterRepository.countByStatus(RegisterStatus.PENDING);
        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);
        long pendingOrders = orderRepository.countByOrderStatus(OrderStatus.PENDING);
        long preparingOrders = orderRepository.countByOrderStatus(OrderStatus.PREPARING);
        long deliveringOrders = orderRepository.countByOrderStatus(OrderStatus.DELIVERING);

        return StatsOverviewResponse.builder()
                .totalUsers(totalUsers)
                .totalRestaurants(totalRestaurants)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue)
                .totalCommission(totalCommission)
                .totalMerchantNet(totalMerchantNet)
                .totalShipperShare(totalShipperShare)
                .commissionRate(BigDecimal.valueOf(commissionRate))
                .activeShippers(activeShippers)
                .lockedUsers(lockedUsers)
                .customerCount(customerCount)
                .ownerCount(ownerCount)
                .shipperCount(shipperCount)
                .pendingRestaurantRegisters(pendingRestaurantRegisters)
                .pendingShipperRegisters(pendingShipperRegisters)
                .pendingReports(pendingReports)
                .avgOrderValue(avgOrderValue)
                .pendingOrders(pendingOrders)
                .preparingOrders(preparingOrders)
                .deliveringOrders(deliveringOrders)
                .build();
    }

    /**
     * Số liệu TỔNG QUAN NGHIỆP VỤ TOÀN HỆ THỐNG cho dashboard admin.
     * Mọi con số tính THẲNG ở DB (không tải đơn rồi tính client-side vốn bị chặn size=2000) → chính xác tuyệt đối.
     */
    @Cacheable("adminInsights")
    @Transactional(readOnly = true)
    public AdminInsightsResponse adminInsights() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last7 = now.minusDays(7);
        LocalDateTime prev7 = now.minusDays(14);
        LocalDateTime since30 = now.minusDays(30);

        // Xu hướng: GTV & đơn hoàn tất 7 ngày qua vs 7 ngày trước
        BigDecimal gmv7d = orderRepository.sumCompletedRevenueBetween(last7, now);
        BigDecimal gmvPrev7d = orderRepository.sumCompletedRevenueBetween(prev7, last7);
        long orders7d = orderRepository.countCompletedBetween(last7, now);
        long ordersPrev7d = orderRepository.countCompletedBetween(prev7, last7);

        // Tăng trưởng thành viên/đối tác (theo created_at)
        long newUsers7d = userRepository.countByCreatedAtAfter(last7);
        long newUsers30d = userRepository.countByCreatedAtAfter(since30);
        long newRestaurants30d = restaurantRepository.countByCreatedAtAfter(since30);
        long newShippers30d = userRepository.countByRoleAndCreatedAtAfter(Role.SHIPPER, since30);

        // Chuỗi GTV theo ngày TOÀN LỊCH SỬ (tính ở server) — {yyyy-MM-dd, gtv, orders}; FE có thanh kéo trượt
        LocalDateTime sinceDaily = now.minusYears(2);
        List<AdminInsightsResponse.DayBucket> dailyGmv = orderRepository.findDailyGmvSince(sinceDaily)
                .stream()
                .map(row -> AdminInsightsResponse.DayBucket.builder()
                        .date(row[0].toString())
                        .gmv(row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString()))
                        .orders(((Number) row[2]).longValue())
                        .build())
                .toList();

        // Giờ cao điểm toàn hệ thống {hour, count}
        List<AdminInsightsResponse.HourBucket> peakHours = orderRepository.findPeakHoursSystemWide()
                .stream()
                .map(row -> AdminInsightsResponse.HourBucket.builder()
                        .hour(((Number) row[0]).intValue())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        // Toàn vẹn thanh toán (đếm theo trạng thái thanh toán toàn hệ thống)
        long paidOrders = orderRepository.countByPaymentStatus(PaymentStatus.PAID);
        long pendingPayment = orderRepository.countByPaymentStatus(PaymentStatus.PENDING);
        long refundedOrders = orderRepository.countByPaymentStatus(PaymentStatus.REFUNDED);
        long failedPayments = orderRepository.countByPaymentStatus(PaymentStatus.FAILED);

        return AdminInsightsResponse.builder()
                .gmv7d(gmv7d)
                .gmvPrev7d(gmvPrev7d)
                .orders7d(orders7d)
                .ordersPrev7d(ordersPrev7d)
                .newUsers7d(newUsers7d)
                .newUsers30d(newUsers30d)
                .newRestaurants30d(newRestaurants30d)
                .newShippers30d(newShippers30d)
                .dailyGmv(dailyGmv)
                .peakHours(peakHours)
                .paidOrders(paidOrders)
                .pendingPayment(pendingPayment)
                .refundedOrders(refundedOrders)
                .failedPayments(failedPayments)
                .build();
    }

    @Cacheable(value = "merchantStats", key = "#restaurantId")
    @Transactional(readOnly = true)
    public MerchantStatsResponse merchantStats(Long merchantId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);

        long totalOrders = orderRepository.countByRestaurantRestaurantId(restaurantId);
        // Lấy completed orders của quán
        long completedOrders = orderRepository.countByRestaurantRestaurantIdAndOrderStatus(restaurantId, OrderStatus.COMPLETED);

        // Doanh thu thực nhận tính từ transactions MERCHANT_EARNING của owner
        BigDecimal revenue = transactionRepository.sumAmountByUserIdAndType(
                restaurant.getOwner().getUserId(),
                org.example.datn.domain.enums.TransactionType.MERCHANT_EARNING);

        // Tính toán các con số liên quan ngược lại từ doanh thu thực nhận
        BigDecimal rate = BigDecimal.valueOf(commissionRate);
        BigDecimal oneMinusRate = BigDecimal.ONE.subtract(rate);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;

        if (oneMinusRate.compareTo(BigDecimal.ZERO) > 0 && revenue.compareTo(BigDecimal.ZERO) > 0) {
            // subtotal = revenue / (1 - rate)
            subtotal = revenue.divide(oneMinusRate, 2, RoundingMode.HALF_UP);
            // commission = subtotal * rate
            commission = subtotal.multiply(rate);
        }

        // KPI vận hành + đánh giá toàn cục (rating tính trên TOÀN BỘ review của quán, không theo trang)
        long cancelledOrders = orderRepository.countByRestaurantRestaurantIdAndOrderStatus(restaurantId, OrderStatus.CANCELLED);
        long pendingOrders = orderRepository.countByRestaurantRestaurantIdAndOrderStatus(restaurantId, OrderStatus.PENDING);
        Double avgRating = reviewRepository.findAverageRatingByRestaurantId(restaurantId); // có thể null nếu chưa có review
        long reviewsCount = reviewRepository.countByRestaurantRestaurantId(restaurantId);
        // AOV = doanh thu thực nhận / số đơn hoàn tất (tránh chia 0)
        BigDecimal avgOrderValue = completedOrders > 0
                ? revenue.divide(BigDecimal.valueOf(completedOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return MerchantStatsResponse.builder()
                .restaurantId(restaurantId)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .revenue(revenue)
                .subtotal(subtotal)
                .commission(commission)
                .commissionRate(rate)
                .cancelledOrders(cancelledOrders)
                .pendingOrders(pendingOrders)
                .avgRating(avgRating)
                .reviewsCount(reviewsCount)
                .avgOrderValue(avgOrderValue)
                .build();
    }

    /**
     * Số liệu TỔNG QUAN NGHIỆP VỤ cho dashboard merchant (xu hướng, giờ cao điểm, khách, thực đơn).
     * Tách khỏi báo cáo tài chính; tính trên TOÀN BỘ đơn để owner liếc một cái thấy sức khoẻ kinh doanh.
     */
    @Cacheable(value = "merchantInsights", key = "#restaurantId")
    @Transactional(readOnly = true)
    public MerchantInsightsResponse merchantInsights(Long merchantId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last7 = now.minusDays(7);
        LocalDateTime prev7 = now.minusDays(14);
        LocalDateTime since30 = now.minusDays(30);
        LocalDateTime sinceDaily = now.minusYears(2); // chuỗi theo ngày: gần như toàn lịch sử (FE có thanh kéo trượt)

        // Xu hướng: doanh thu món & số đơn hoàn tất 7 ngày qua vs 7 ngày trước
        BigDecimal revenue7d = orderRepository.sumCompletedSubtotalByRestaurantBetween(restaurantId, last7, now);
        BigDecimal revenuePrev7d = orderRepository.sumCompletedSubtotalByRestaurantBetween(restaurantId, prev7, last7);
        long orders7d = orderRepository.countCompletedByRestaurantBetween(restaurantId, last7, now);
        long ordersPrev7d = orderRepository.countCompletedByRestaurantBetween(restaurantId, prev7, last7);

        // Chuỗi doanh thu món theo ngày (toàn lịch sử) — {yyyy-MM-dd, revenue, orders}
        List<MerchantInsightsResponse.DayBucket> dailyRevenue = orderRepository.findDailyRevenueByRestaurantSince(restaurantId, sinceDaily)
                .stream()
                .map(row -> MerchantInsightsResponse.DayBucket.builder()
                        .date(row[0].toString())
                        .revenue(row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString()))
                        .orders(((Number) row[2]).longValue())
                        .build())
                .toList();

        // Giờ cao điểm (map {hour, count} từ Object[])
        List<MerchantInsightsResponse.HourBucket> peakHours = orderRepository.findPeakHoursByRestaurant(restaurantId)
                .stream()
                .map(row -> MerchantInsightsResponse.HourBucket.builder()
                        .hour(((Number) row[0]).intValue())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        // Khách hàng (trên đơn hoàn tất)
        long uniqueCustomers = orderRepository.countDistinctCustomersByRestaurant(restaurantId);
        long returningCustomers = orderRepository.findReturningCustomerIdsByRestaurant(restaurantId).size();
        long newCustomers30d = orderRepository.findNewCustomerIdsByRestaurantSince(restaurantId, since30).size();

        // Sức khoẻ thực đơn
        long menuTotal = foodRepository.countByRestaurantRestaurantId(restaurantId);
        long menuAvailable = foodRepository.countByRestaurantRestaurantIdAndStatusTrueAndIsAvailableTrue(restaurantId);
        long menuOutOfStock = foodRepository.countByRestaurantRestaurantIdAndStatusTrueAndIsAvailableFalse(restaurantId);
        long menuHidden = foodRepository.countByRestaurantRestaurantIdAndStatusFalse(restaurantId);
        // Món đang bán (status=true) nhưng chưa có lượt bán nào (đếm lại per-food, thực đơn nhỏ nên chấp nhận)
        long menuNoSales = foodRepository.findByRestaurantIdForMerchant(restaurantId).stream()
                .filter(f -> {
                    Integer sold = orderRepository.countCompletedQuantityByFoodId(f.getFoodId());
                    return sold == null || sold == 0;
                })
                .count();

        return MerchantInsightsResponse.builder()
                .revenue7d(revenue7d)
                .revenuePrev7d(revenuePrev7d)
                .orders7d(orders7d)
                .ordersPrev7d(ordersPrev7d)
                .dailyRevenue(dailyRevenue)
                .peakHours(peakHours)
                .uniqueCustomers(uniqueCustomers)
                .returningCustomers(returningCustomers)
                .newCustomers30d(newCustomers30d)
                .menuTotal(menuTotal)
                .menuAvailable(menuAvailable)
                .menuOutOfStock(menuOutOfStock)
                .menuHidden(menuHidden)
                .menuNoSales(menuNoSales)
                .build();
    }

    // ═══════════════════ BÁO CÁO THỐNG KÊ (gộp ở server, nhanh) ═══════════════════

    /** Quy đổi range → cửa sổ [from, to). "all" dùng mốc rất cũ để bao toàn bộ. */
    private LocalDateTime[] resolveRange(String range) {
        LocalDate today = LocalDate.now();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from;
        switch (range == null ? "all" : range) {
            case "today" -> from = today.atStartOfDay();
            case "7days" -> from = to.minusDays(7);
            case "30days" -> from = to.minusDays(30);
            case "90days" -> from = to.minusDays(90);
            case "thisWeek" -> from = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7).atStartOfDay(); // Thứ 2 đầu tuần
            case "thisMonth" -> from = today.withDayOfMonth(1).atStartOfDay();
            case "lastMonth" -> { // tháng trước trọn vẹn → to = đầu tháng này
                from = today.withDayOfMonth(1).minusMonths(1).atStartOfDay();
                to = today.withDayOfMonth(1).atStartOfDay();
            }
            case "thisYear" -> from = today.withDayOfYear(1).atStartOfDay();
            default -> from = LocalDateTime.of(2000, 1, 1, 0, 0); // "all"
        }
        return new LocalDateTime[]{from, to};
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return (o instanceof BigDecimal b) ? b : new BigDecimal(o.toString());
    }

    private static long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /**
     * BÁO CÁO TÀI CHÍNH NHÀ HÀNG — gộp toàn bộ ở DB theo cửa sổ thời gian (thay tính client-side).
     */
    @Cacheable(value = "merchantReport", key = "#restaurantId + '-' + #range + '-' + #dow + '-' + #month + '-' + #year")
    @Transactional(readOnly = true)
    public MerchantReportResponse merchantReport(Long merchantId, Long restaurantId, String range, Integer dow, Integer month, Integer year) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);

        LocalDateTime[] w = resolveRange(range);
        LocalDateTime from = w[0], to = w[1];
        BigDecimal rate = BigDecimal.valueOf(commissionRate);

        // Tài chính đơn hoàn tất: {total, subtotal, shipping, count} (aggregate → đúng 1 hàng)
        List<Object[]> finRows = orderRepository.financeCompletedByRestaurantBetween(restaurantId, from, to, dow, month, year);
        Object[] fin = finRows.isEmpty() ? new Object[]{null, null, null, 0L} : finRows.get(0);
        BigDecimal gtv = bd(fin[0]);
        BigDecimal subtotal = bd(fin[1]);
        BigDecimal shipping = bd(fin[2]);
        // Đơn hoàn tất TẠO DOANH THU (đã loại refund) — dùng làm mẫu số AOV để khớp doanh thu net.
        long completedNet = lng(fin[3]);
        BigDecimal commission = subtotal.multiply(rate);
        BigDecimal earnings = subtotal.subtract(commission);
        BigDecimal aov = completedNet > 0
                ? subtotal.divide(BigDecimal.valueOf(completedNet), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Phân bố trạng thái đơn → suy ra tổng đơn & đơn huỷ
        List<MerchantReportResponse.Bucket> statusDist = orderRepository.statusDistByRestaurantBetween(restaurantId, from, to, dow, month, year)
                .stream().map(r -> MerchantReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();
        long totalOrders = statusDist.stream().mapToLong(MerchantReportResponse.Bucket::getCount).sum();
        long cancelledOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.CANCELLED.name()))
                .mapToLong(MerchantReportResponse.Bucket::getCount).sum();
        // Đơn hoàn tất HIỂN THỊ = MỌI đơn COMPLETED (kể cả đơn sau đó hoàn tiền) → KHỚP số ở Dashboard.
        long completedOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.COMPLETED.name()))
                .mapToLong(MerchantReportResponse.Bucket::getCount).sum();

        List<MerchantReportResponse.Bucket> paymentDist = orderRepository.paymentDistByRestaurantBetween(restaurantId, from, to, dow, month, year)
                .stream().map(r -> MerchantReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();

        List<MerchantReportResponse.DayPoint> daily = orderRepository.dailyByRestaurantBetween(restaurantId, from, to, dow, month, year)
                .stream().map(r -> MerchantReportResponse.DayPoint.builder()
                        .date(r[0].toString()).subtotal(bd(r[1])).orders(lng(r[2])).build())
                .toList();

        long uniqueCustomers = orderRepository.countDistinctCustomersByRestaurantBetween(restaurantId, from, to, dow, month, year);

        List<MerchantReportResponse.TopFood> topFoods = orderRepository
                .topFoodsByRestaurantBetween(restaurantId, from, to, dow, month, year, PageRequest.of(0, 10))
                .stream().map(r -> MerchantReportResponse.TopFood.builder()
                        .name((String) r[0]).qty(lng(r[1])).revenue(bd(r[2])).build())
                .toList();

        return MerchantReportResponse.builder()
                .range(range).commissionRate(rate)
                .gtv(gtv).subtotal(subtotal).commission(commission).earnings(earnings).shipping(shipping).aov(aov)
                .totalOrders(totalOrders).completedOrders(completedOrders).cancelledOrders(cancelledOrders)
                .uniqueCustomers(uniqueCustomers)
                .daily(daily).paymentDist(paymentDist).statusDist(statusDist).topFoods(topFoods)
                .build();
    }

    /**
     * BÁO CÁO PHÂN TÍCH DOANH THU HỆ THỐNG — gộp toàn bộ ở DB (thay việc tải size=2000 đơn + size=1500 user).
     */
    @Cacheable(value = "adminReport", key = "#range + '-' + #dow + '-' + #month + '-' + #year")
    @Transactional(readOnly = true)
    public AdminReportResponse adminReport(String range, Integer dow, Integer month, Integer year) {
        LocalDateTime[] w = resolveRange(range);
        LocalDateTime from = w[0], to = w[1];
        BigDecimal rate = BigDecimal.valueOf(commissionRate);

        List<Object[]> finRows = orderRepository.financeCompletedSystemBetween(from, to, dow, month, year);
        Object[] fin = finRows.isEmpty() ? new Object[]{null, null, null, 0L} : finRows.get(0);
        BigDecimal gtv = bd(fin[0]);
        BigDecimal subtotal = bd(fin[1]);
        BigDecimal shipping = bd(fin[2]);
        // Đơn hoàn tất TẠO DOANH THU (đã loại refund) — dùng làm mẫu số AOV để khớp doanh thu net.
        long completedNet = lng(fin[3]);
        BigDecimal commission = subtotal.multiply(rate);
        BigDecimal merchantNet = subtotal.subtract(commission);
        BigDecimal aov = completedNet > 0
                ? subtotal.divide(BigDecimal.valueOf(completedNet), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<AdminReportResponse.Bucket> statusDist = orderRepository.statusDistSystemBetween(from, to, dow, month, year)
                .stream().map(r -> AdminReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();
        long totalOrders = statusDist.stream().mapToLong(AdminReportResponse.Bucket::getCount).sum();
        long cancelledOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.CANCELLED.name()))
                .mapToLong(AdminReportResponse.Bucket::getCount).sum();
        // Đơn hoàn tất HIỂN THỊ = MỌI đơn COMPLETED (kể cả đơn sau đó hoàn tiền) → KHỚP số ở Dashboard/overview.
        long completedOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.COMPLETED.name()))
                .mapToLong(AdminReportResponse.Bucket::getCount).sum();

        List<AdminReportResponse.Bucket> paymentDist = orderRepository.paymentDistSystemBetween(from, to, dow, month, year)
                .stream().map(r -> AdminReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();

        List<AdminReportResponse.DayPoint> daily = orderRepository.dailySystemBetween(from, to, dow, month, year)
                .stream().map(r -> AdminReportResponse.DayPoint.builder()
                        .date(r[0].toString()).gtv(bd(r[1])).subtotal(bd(r[2])).orders(lng(r[3])).build())
                .toList();

        long uniqueCustomers = orderRepository.countDistinctCustomersSystemBetween(from, to, dow, month, year);

        List<AdminReportResponse.TopRestaurant> topRestaurants = orderRepository
                .topRestaurantsSystemBetween(from, to, dow, month, year, PageRequest.of(0, 10))
                .stream().map(r -> {
                    BigDecimal sub = bd(r[2]);
                    BigDecimal comm = sub.multiply(rate);
                    return AdminReportResponse.TopRestaurant.builder()
                            .name((String) r[0]).orders(lng(r[1]))
                            .subtotal(sub).commission(comm).netShare(sub.subtract(comm)).build();
                })
                .toList();

        return AdminReportResponse.builder()
                .range(range).commissionRate(rate)
                .gtv(gtv).subtotal(subtotal).commission(commission).merchantNet(merchantNet).shipping(shipping).aov(aov)
                .totalOrders(totalOrders).completedOrders(completedOrders).cancelledOrders(cancelledOrders)
                .uniqueCustomers(uniqueCustomers)
                .daily(daily).paymentDist(paymentDist).statusDist(statusDist).topRestaurants(topRestaurants)
                .build();
    }

    /**
     * PHÂN TÍCH VOUCHER cho dashboard admin — số thật từ đơn hoàn tất có gắn voucher.
     */
    @Cacheable(value = "voucherAnalytics", key = "#range")
    @Transactional(readOnly = true)
    public VoucherAnalyticsResponse voucherAnalytics(String range) {
        LocalDateTime[] w = resolveRange(range);
        LocalDateTime from = w[0], to = w[1];

        List<Object[]> finRows = orderRepository.voucherFinanceBetween(from, to);
        Object[] fin = finRows.isEmpty() ? new Object[]{0L, null, null} : finRows.get(0);
        long redeemedOrders = lng(fin[0]);
        BigDecimal discountCost = bd(fin[1]);
        BigDecimal voucherRevenue = bd(fin[2]);
        BigDecimal avgDiscount = redeemedOrders > 0
                ? discountCost.divide(BigDecimal.valueOf(redeemedOrders), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<VoucherAnalyticsResponse.TopVoucher> topVouchers = orderRepository
                .topVouchersBetween(from, to, PageRequest.of(0, 5))
                .stream().map(r -> VoucherAnalyticsResponse.TopVoucher.builder()
                        .code((String) r[0]).name((String) r[1]).uses(lng(r[2])).discount(bd(r[3])).build())
                .toList();

        List<VoucherAnalyticsResponse.DayUsage> dailyUsage = orderRepository.dailyVoucherUsageBetween(from, to)
                .stream().map(r -> VoucherAnalyticsResponse.DayUsage.builder()
                        .date(r[0].toString()).uses(lng(r[1])).discount(bd(r[2])).build())
                .toList();

        return VoucherAnalyticsResponse.builder()
                .range(range)
                .totalVouchers(voucherRepository.count())
                .activeVouchers(voucherRepository.countByStatus(VoucherStatus.ACTIVE))
                .redeemedOrders(redeemedOrders)
                .discountCost(discountCost)
                .voucherRevenue(voucherRevenue)
                .avgDiscountPerOrder(avgDiscount)
                .topVouchers(topVouchers)
                .dailyUsage(dailyUsage)
                .build();
    }

    // ─── Shipper: tổng hợp thu nhập (gộp server-side, không bị chặn size=1000) ───
    private static String weekdayKey(DayOfWeek d) {
        switch (d) {
            case MONDAY:    return "T2";
            case TUESDAY:   return "T3";
            case WEDNESDAY: return "T4";
            case THURSDAY:  return "T5";
            case FRIDAY:    return "T6";
            case SATURDAY:  return "T7";
            default:        return "CN";
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "shipperInsights", key = "#shipperUserId")
    public ShipperInsightsResponse shipperInsights(Long shipperUserId) {
        Shipper shipper = shipperRepository.findByUserUserId(shipperUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        LocalDate today = LocalDate.now();
        LocalDate start7dDate = today.minusDays(6);
        LocalDate weekStartDate = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7); // Thứ 2
        LocalDate weekEndDate = weekStartDate.plusDays(7); // exclusive
        LocalDate monthStartDate = today.withDayOfMonth(1);
        LocalDate lastMonthStartDate = monthStartDate.minusMonths(1);
        LocalDate since6mo = today.minusMonths(5).withDayOfMonth(1); // đầu tháng 6 tháng trước

        String[] order = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        // MySQL DAYOFWEEK: 1=CN..7=T7 → key
        String[] dowKey = {"", "CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        Map<String, Long> dayMap = new HashMap<>();

        // 6 tháng gần đây (kể cả tháng hiện tại)
        List<ShipperInsightsResponse.MonthAmount> monthly = new ArrayList<>();
        Map<String, Integer> monthIndex = new HashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = today.minusMonths(i).withDayOfMonth(1);
            monthIndex.put(d.getYear() + "-" + d.getMonthValue(), monthly.size());
            monthly.add(ShipperInsightsResponse.MonthAmount.builder()
                    .label(String.format("%02d/%d", d.getMonthValue(), d.getYear())).amount(0).build());
        }

        // Tổng thu nhập cả sự nghiệp: {SUM(fee), COUNT, MAX(fee)}
        long totalEarnings = 0, maxFee = 0; int completedCount = 0;
        List<Object[]> totalsRows = orderRepository.shipperEarningTotals(shipperUserId);
        if (!totalsRows.isEmpty()) {
            Object[] t = totalsRows.get(0);
            totalEarnings = ((Number) t[0]).longValue();
            completedCount = ((Number) t[1]).intValue();
            maxFee = ((Number) t[2]).longValue();
        }

        // Chuỗi thu nhập theo NGÀY (6 tháng gần đây) → suy ra mọi cửa sổ thời gian
        long todayEarnings = 0, week7dEarnings = 0, thisWeekEarnings = 0;
        long thisMonthEarnings = 0, lastMonthEarnings = 0;
        int todayCount = 0, week7dCount = 0, thisMonthCount = 0, activeDayCount = 0;
        for (Object[] row : orderRepository.findShipperDailyEarningsSince(shipperUserId, since6mo.atStartOfDay())) {
            LocalDate d = LocalDate.parse(row[0].toString());
            long amt = ((Number) row[1]).longValue();
            int cnt = ((Number) row[2]).intValue();
            Integer mi = monthIndex.get(d.getYear() + "-" + d.getMonthValue());
            if (mi != null) { ShipperInsightsResponse.MonthAmount ma = monthly.get(mi); ma.setAmount(ma.getAmount() + amt); }
            if (d.equals(today)) { todayEarnings += amt; todayCount += cnt; }
            if (!d.isBefore(start7dDate)) { week7dEarnings += amt; week7dCount += cnt; }
            if (!d.isBefore(weekStartDate) && d.isBefore(weekEndDate)) {
                thisWeekEarnings += amt;
                dayMap.merge(weekdayKey(d.getDayOfWeek()), amt, Long::sum);
            }
            if (!d.isBefore(monthStartDate)) { thisMonthEarnings += amt; thisMonthCount += cnt; activeDayCount++; }
            else if (!d.isBefore(lastMonthStartDate)) { lastMonthEarnings += amt; }
        }

        List<ShipperInsightsResponse.DayAmount> daily = new ArrayList<>();
        for (String k : order) daily.add(ShipperInsightsResponse.DayAmount.builder().day(k).amount(dayMap.getOrDefault(k, 0L)).build());

        // Thứ có thu nhập cao nhất (cả sự nghiệp)
        String bestWeekday = null; long bestAmt = 0;
        for (Object[] row : orderRepository.findShipperEarningsByWeekday(shipperUserId)) {
            int dow = ((Number) row[0]).intValue();     // 1=CN..7=T7
            long v = ((Number) row[1]).longValue();
            if (v > bestAmt && dow >= 1 && dow <= 7) { bestAmt = v; bestWeekday = dowKey[dow]; }
        }

        // Giờ vàng (cả sự nghiệp)
        int peakHourIdx = 0; long peakAmt = 0;
        for (Object[] row : orderRepository.findShipperEarningsByHour(shipperUserId)) {
            int h = ((Number) row[0]).intValue();
            long v = ((Number) row[1]).longValue();
            if (v > peakAmt) { peakAmt = v; peakHourIdx = h; }
        }

        int monthDelta = lastMonthEarnings > 0
                ? (int) Math.round((thisMonthEarnings - lastMonthEarnings) * 100.0 / lastMonthEarnings)
                : (thisMonthEarnings > 0 ? 100 : 0);
        long avgPerActiveDay = activeDayCount > 0 ? thisMonthEarnings / activeDayCount : 0;

        Double liveAvg = reviewRepository.findAverageRatingByShipperId(shipper.getShipperId());
        double rating = liveAvg != null ? liveAvg
                : (shipper.getAvgRating() != null ? shipper.getAvgRating().doubleValue() : 0.0);
        int ratedCount = (int) reviewRepository.countByShipperShipperIdAndShipperRatingIsNotNull(shipper.getShipperId());

        return ShipperInsightsResponse.builder()
                .totalEarnings(totalEarnings).completedCount(completedCount)
                .todayEarnings(todayEarnings).todayCount(todayCount)
                .week7dEarnings(week7dEarnings).week7dCount(week7dCount)
                .thisWeekEarnings(thisWeekEarnings)
                .thisMonthEarnings(thisMonthEarnings).thisMonthCount(thisMonthCount)
                .lastMonthEarnings(lastMonthEarnings).monthDelta(monthDelta)
                .activeDayCount(activeDayCount).avgPerActiveDay(avgPerActiveDay)
                .bestWeekday(bestWeekday).bestWeekdayAmount(bestAmt)
                .peakHourIdx(peakHourIdx).peakHourAmount(peakAmt).maxFee(maxFee)
                .rating(rating).ratedCount(ratedCount)
                .daily(daily).monthly(monthly)
                .build();
    }
}

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
import org.example.datn.domain.enums.PaymentStatus;
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

        // Chuỗi GTV theo ngày 30 ngày gần nhất (tính ở server) — {yyyy-MM-dd, gtv, orders}
        List<AdminInsightsResponse.DayBucket> dailyGmv = orderRepository.findDailyGmvSince(since30)
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
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from;
        switch (range == null ? "all" : range) {
            case "7days" -> from = to.minusDays(7);
            case "30days" -> from = to.minusDays(30);
            case "thisMonth" -> from = LocalDate.now().withDayOfMonth(1).atStartOfDay();
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
    @Cacheable(value = "merchantReport", key = "#restaurantId + '-' + #range")
    @Transactional(readOnly = true)
    public MerchantReportResponse merchantReport(Long merchantId, Long restaurantId, String range) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);

        LocalDateTime[] w = resolveRange(range);
        LocalDateTime from = w[0], to = w[1];
        BigDecimal rate = BigDecimal.valueOf(commissionRate);

        // Tài chính đơn hoàn tất: {total, subtotal, shipping, count} (aggregate → đúng 1 hàng)
        List<Object[]> finRows = orderRepository.financeCompletedByRestaurantBetween(restaurantId, from, to);
        Object[] fin = finRows.isEmpty() ? new Object[]{null, null, null, 0L} : finRows.get(0);
        BigDecimal gtv = bd(fin[0]);
        BigDecimal subtotal = bd(fin[1]);
        BigDecimal shipping = bd(fin[2]);
        long completedOrders = lng(fin[3]);
        BigDecimal commission = subtotal.multiply(rate);
        BigDecimal earnings = subtotal.subtract(commission);
        BigDecimal aov = completedOrders > 0
                ? subtotal.divide(BigDecimal.valueOf(completedOrders), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Phân bố trạng thái đơn → suy ra tổng đơn & đơn huỷ
        List<MerchantReportResponse.Bucket> statusDist = orderRepository.statusDistByRestaurantBetween(restaurantId, from, to)
                .stream().map(r -> MerchantReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();
        long totalOrders = statusDist.stream().mapToLong(MerchantReportResponse.Bucket::getCount).sum();
        long cancelledOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.CANCELLED.name()))
                .mapToLong(MerchantReportResponse.Bucket::getCount).sum();

        List<MerchantReportResponse.Bucket> paymentDist = orderRepository.paymentDistByRestaurantBetween(restaurantId, from, to)
                .stream().map(r -> MerchantReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();

        List<MerchantReportResponse.DayPoint> daily = orderRepository.dailyByRestaurantBetween(restaurantId, from, to)
                .stream().map(r -> MerchantReportResponse.DayPoint.builder()
                        .date(r[0].toString()).subtotal(bd(r[1])).orders(lng(r[2])).build())
                .toList();

        long uniqueCustomers = orderRepository.countDistinctCustomersByRestaurantBetween(restaurantId, from, to);

        List<MerchantReportResponse.TopFood> topFoods = orderRepository
                .topFoodsByRestaurantBetween(restaurantId, from, to, PageRequest.of(0, 5))
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
    @Cacheable(value = "adminReport", key = "#range")
    @Transactional(readOnly = true)
    public AdminReportResponse adminReport(String range) {
        LocalDateTime[] w = resolveRange(range);
        LocalDateTime from = w[0], to = w[1];
        BigDecimal rate = BigDecimal.valueOf(commissionRate);

        List<Object[]> finRows = orderRepository.financeCompletedSystemBetween(from, to);
        Object[] fin = finRows.isEmpty() ? new Object[]{null, null, null, 0L} : finRows.get(0);
        BigDecimal gtv = bd(fin[0]);
        BigDecimal subtotal = bd(fin[1]);
        BigDecimal shipping = bd(fin[2]);
        long completedOrders = lng(fin[3]);
        BigDecimal commission = subtotal.multiply(rate);
        BigDecimal merchantNet = subtotal.subtract(commission);
        BigDecimal aov = completedOrders > 0
                ? subtotal.divide(BigDecimal.valueOf(completedOrders), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<AdminReportResponse.Bucket> statusDist = orderRepository.statusDistSystemBetween(from, to)
                .stream().map(r -> AdminReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();
        long totalOrders = statusDist.stream().mapToLong(AdminReportResponse.Bucket::getCount).sum();
        long cancelledOrders = statusDist.stream()
                .filter(b -> b.getKey().equals(OrderStatus.CANCELLED.name()))
                .mapToLong(AdminReportResponse.Bucket::getCount).sum();

        List<AdminReportResponse.Bucket> paymentDist = orderRepository.paymentDistSystemBetween(from, to)
                .stream().map(r -> AdminReportResponse.Bucket.builder()
                        .key(((Enum<?>) r[0]).name()).count(lng(r[1])).amount(bd(r[2])).build())
                .toList();

        List<AdminReportResponse.DayPoint> daily = orderRepository.dailySystemBetween(from, to)
                .stream().map(r -> AdminReportResponse.DayPoint.builder()
                        .date(r[0].toString()).gtv(bd(r[1])).subtotal(bd(r[2])).orders(lng(r[3])).build())
                .toList();

        long uniqueCustomers = orderRepository.countDistinctCustomersSystemBetween(from, to);

        List<AdminReportResponse.TopRestaurant> topRestaurants = orderRepository
                .topRestaurantsSystemBetween(from, to, PageRequest.of(0, 5))
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
}

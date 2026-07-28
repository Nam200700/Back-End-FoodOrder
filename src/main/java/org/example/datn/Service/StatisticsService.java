package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.DTO.response.stats.MerchantInsightsResponse;
import org.example.datn.DTO.response.stats.MerchantStatsResponse;
import org.example.datn.DTO.response.stats.StatsOverviewResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
}

package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Order;
import org.example.datn.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends BaseRepository<Order, Long> {

    Page<Order> findByCustomerUserIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findByCustomerUserIdAndOrderStatusOrderByCreatedAtDesc(Long customerId, OrderStatus status, Pageable pageable);

    Page<Order> findByRestaurantRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Page<Order> findByRestaurantRestaurantIdAndOrderStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status, Pageable pageable);

    Page<Order> findByRestaurantRestaurantIdAndOrderStatusIn(Long restaurantId, List<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByShipperUserId(Long shipperId, Pageable pageable);

    /** TẤT CẢ đơn theo shipper + trạng thái (không phân trang) để gộp thu nhập server-side. */
    List<Order> findByShipperUserIdAndOrderStatus(Long shipperId, OrderStatus status);

    /** Fetch-join items + food to avoid N+1 when rendering a single order. */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.food
            WHERE o.orderId = :id
            """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.customer c " +
           "JOIN FETCH o.restaurant r " +
           "JOIN FETCH r.owner w " +
           "WHERE o.orderId = :orderId")
    Optional<Order> findDetailById(@Param("orderId") Long orderId);


    /** Orders confirmed by the merchant and not yet picked up by any shipper. */
    @Query("""
            SELECT o FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.READY_FOR_PICKUP
              AND o.shipper IS NULL
            ORDER BY o.createdAt ASC
            """)
    List<Order> findAvailableOrders();

    // ─── Statistics ───────────────────────────────────────────
    long countByOrderStatus(OrderStatus status);

    long countByRestaurantRestaurantId(Long restaurantId);

    long countByRestaurantRestaurantIdAndOrderStatus(Long restaurantId, OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(oi.quantity), 0) FROM Order o
            JOIN o.items oi
            WHERE oi.food.foodId = :foodId AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            """)
    Integer countCompletedQuantityByFoodId(@Param("foodId") Long foodId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus = :status")
    BigDecimal sumRevenueByStatus(@Param("status") OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.restaurant.restaurantId = :rid AND o.orderStatus = :status
            """)
    BigDecimal sumRevenueByRestaurantAndStatus(@Param("rid") Long restaurantId, @Param("status") OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
            """)
    BigDecimal sumCompletedRevenueExcludeRefunded();

    @Query("""
            SELECT COALESCE(SUM(o.subtotalAmount), 0) FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
            """)
    BigDecimal sumCompletedSubtotalExcludeRefunded();

    // ─── Dashboard insights merchant (tổng quan nghiệp vụ) ───────────────────

    /** Doanh thu món (subtotal) đơn hoàn tất của quán trong khoảng [from, to). */
    @Query("""
            SELECT COALESCE(SUM(o.subtotalAmount), 0) FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal sumCompletedSubtotalByRestaurantBetween(@Param("rid") Long restaurantId,
                                                       @Param("from") java.time.LocalDateTime from,
                                                       @Param("to") java.time.LocalDateTime to);

    /** Số đơn hoàn tất của quán trong khoảng [from, to). */
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    long countCompletedByRestaurantBetween(@Param("rid") Long restaurantId,
                                           @Param("from") java.time.LocalDateTime from,
                                           @Param("to") java.time.LocalDateTime to);

    /** Số đơn hoàn tất gom theo giờ trong ngày → {hour, count}. */
    @Query("""
            SELECT FUNCTION('HOUR', o.createdAt), COUNT(o) FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            GROUP BY FUNCTION('HOUR', o.createdAt)
            ORDER BY FUNCTION('HOUR', o.createdAt)
            """)
    List<Object[]> findPeakHoursByRestaurant(@Param("rid") Long restaurantId);

    /** Số khách (distinct) đã có đơn hoàn tất ở quán. */
    @Query("""
            SELECT COUNT(DISTINCT o.customer.userId) FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            """)
    long countDistinctCustomersByRestaurant(@Param("rid") Long restaurantId);

    /** Id khách có >= 2 đơn hoàn tất (khách quay lại) — service lấy .size(). */
    @Query("""
            SELECT o.customer.userId FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            GROUP BY o.customer.userId
            HAVING COUNT(o) >= 2
            """)
    List<Long> findReturningCustomerIdsByRestaurant(@Param("rid") Long restaurantId);

    /** Id khách có đơn hoàn tất ĐẦU TIÊN kể từ :since (khách mới) — service lấy .size(). */
    @Query("""
            SELECT o.customer.userId FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            GROUP BY o.customer.userId
            HAVING MIN(o.createdAt) >= :since
            """)
    List<Long> findNewCustomerIdsByRestaurantSince(@Param("rid") Long restaurantId,
                                                   @Param("since") java.time.LocalDateTime since);

    @Query("""
            SELECT o FROM Order o 
            JOIN FETCH o.customer c
            JOIN FETCH o.restaurant r
            WHERE (:keyword IS NULL OR
                   CAST(o.orderId AS string) LIKE LOWER(CONCAT('%', :keyword, '%')) OR 
                   LOWER(r.restaurantName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR 
                   LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR 
                   LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:status IS NULL OR o.orderStatus = :status) 
            AND (:statuses IS NULL OR o.orderStatus IN :statuses)
            """)
    Page<Order> searchAdminOrders(
            @Param("keyword") String keyword,
            @Param("status") OrderStatus status,
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable
    );

    long countByOrderStatusIn(List<OrderStatus> orderStatuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED")
    BigDecimal sumTotalGmv();

    // ─── Dashboard insights ADMIN (tổng quan nghiệp vụ toàn hệ thống, không cap size) ───────

    /** GTV (totalAmount) đơn hoàn tất TOÀN HỆ THỐNG trong khoảng [from, to), loại refund. */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal sumCompletedRevenueBetween(@Param("from") java.time.LocalDateTime from,
                                          @Param("to") java.time.LocalDateTime to);

    /** Số đơn hoàn tất TOÀN HỆ THỐNG trong khoảng [from, to). */
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    long countCompletedBetween(@Param("from") java.time.LocalDateTime from,
                               @Param("to") java.time.LocalDateTime to);

    /** Chuỗi GTV theo NGÀY (đơn hoàn tất, loại refund) kể từ :since → {yyyy-MM-dd, gtv, count}. Native cho hàm DATE(). */
    @Query(value = """
            SELECT DATE(created_at) AS d, COALESCE(SUM(total_amount), 0) AS gtv, COUNT(*) AS cnt
            FROM orders
            WHERE order_status = 'COMPLETED' AND payment_status <> 'REFUNDED'
              AND created_at >= :since
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<Object[]> findDailyGmvSince(@Param("since") java.time.LocalDateTime since);

    /** Chuỗi doanh thu món (subtotal, đơn hoàn tất, loại refund) theo NGÀY của MỘT quán kể từ :since → {yyyy-MM-dd, revenue, count}. */
    @Query(value = """
            SELECT DATE(created_at) AS d, COALESCE(SUM(subtotal_amount), 0) AS rev, COUNT(*) AS cnt
            FROM orders
            WHERE order_status = 'COMPLETED' AND payment_status <> 'REFUNDED'
              AND restaurant_id = :rid
              AND created_at >= :since
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<Object[]> findDailyRevenueByRestaurantSince(@Param("rid") Long restaurantId,
                                                     @Param("since") java.time.LocalDateTime since);

    /** Giờ cao điểm toàn hệ thống: số đơn hoàn tất gom theo giờ trong ngày → {hour, count}. */
    @Query("""
            SELECT FUNCTION('HOUR', o.createdAt), COUNT(o) FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
            GROUP BY FUNCTION('HOUR', o.createdAt)
            ORDER BY FUNCTION('HOUR', o.createdAt)
            """)
    List<Object[]> findPeakHoursSystemWide();

    /** Đếm đơn theo trạng thái thanh toán (toàn vẹn thanh toán toàn hệ thống). */
    long countByPaymentStatus(org.example.datn.domain.enums.PaymentStatus paymentStatus);

    // ═══ BÁO CÁO THỐNG KÊ (gộp ở server theo cửa sổ [from,to) — thay cho tính client-side) ═══

    // ─── Merchant (scoped theo restaurantId) ───

    /** Tài chính đơn hoàn tất của quán: {SUM(total), SUM(subtotal), SUM(shipping), COUNT}. */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount),0), COALESCE(SUM(o.subtotalAmount),0),
                   COALESCE(SUM(o.shippingFee),0), COUNT(o)
            FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.createdAt >= :from AND o.createdAt < :to
              AND (:dow IS NULL OR FUNCTION('DAYOFWEEK', o.createdAt) = :dow)
              AND (:month IS NULL OR FUNCTION('MONTH', o.createdAt) = :month)
              AND (:year IS NULL OR FUNCTION('YEAR', o.createdAt) = :year)
            """)
    List<Object[]> financeCompletedByRestaurantBetween(@Param("rid") Long rid,
                                                       @Param("from") java.time.LocalDateTime from,
                                                       @Param("to") java.time.LocalDateTime to,
                                                       @Param("dow") Integer dow,
                                                       @Param("month") Integer month,
                                                       @Param("year") Integer year);

    /** Phân bố trạng thái ĐƠN của quán trong kỳ: {orderStatus, count, sum(total)}. */
    @Query("""
            SELECT o.orderStatus, COUNT(o), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.createdAt >= :from AND o.createdAt < :to
              AND (:dow IS NULL OR FUNCTION('DAYOFWEEK', o.createdAt) = :dow)
              AND (:month IS NULL OR FUNCTION('MONTH', o.createdAt) = :month)
              AND (:year IS NULL OR FUNCTION('YEAR', o.createdAt) = :year)
            GROUP BY o.orderStatus
            """)
    List<Object[]> statusDistByRestaurantBetween(@Param("rid") Long rid,
                                                 @Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to,
                                                 @Param("dow") Integer dow,
                                                 @Param("month") Integer month,
                                                 @Param("year") Integer year);

    /** Phân bố trạng thái THANH TOÁN của quán trong kỳ: {paymentStatus, count, sum(total)}. */
    @Query("""
            SELECT o.paymentStatus, COUNT(o), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.createdAt >= :from AND o.createdAt < :to
              AND (:dow IS NULL OR FUNCTION('DAYOFWEEK', o.createdAt) = :dow)
              AND (:month IS NULL OR FUNCTION('MONTH', o.createdAt) = :month)
              AND (:year IS NULL OR FUNCTION('YEAR', o.createdAt) = :year)
            GROUP BY o.paymentStatus
            """)
    List<Object[]> paymentDistByRestaurantBetween(@Param("rid") Long rid,
                                                  @Param("from") java.time.LocalDateTime from,
                                                  @Param("to") java.time.LocalDateTime to,
                                                  @Param("dow") Integer dow,
                                                  @Param("month") Integer month,
                                                  @Param("year") Integer year);

    /** Chuỗi ngày của quán: {yyyy-MM-dd, subtotal(đơn hoàn tất), count(mọi đơn)}. Native cho DATE(). */
    @Query(value = """
            SELECT DATE(created_at) AS d,
                   COALESCE(SUM(CASE WHEN order_status='COMPLETED' AND payment_status<>'REFUNDED'
                                     THEN subtotal_amount ELSE 0 END),0) AS sub,
                   COUNT(*) AS cnt
            FROM orders
            WHERE restaurant_id = :rid AND created_at >= :from AND created_at < :to
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<Object[]> dailyByRestaurantBetween(@Param("rid") Long rid,
                                            @Param("from") java.time.LocalDateTime from,
                                            @Param("to") java.time.LocalDateTime to);

    /** Số khách duy nhất của quán trong kỳ (mọi trạng thái). */
    @Query("""
            SELECT COUNT(DISTINCT o.customer.userId) FROM Order o
            WHERE o.restaurant.restaurantId = :rid
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    long countDistinctCustomersByRestaurantBetween(@Param("rid") Long rid,
                                                   @Param("from") java.time.LocalDateTime from,
                                                   @Param("to") java.time.LocalDateTime to);

    /** Top món bán chạy của quán trong kỳ (đơn hoàn tất): {foodName, SUM(qty), SUM(qty*price)}. Dùng Pageable để limit. */
    @Query("""
            SELECT oi.foodName, COALESCE(SUM(oi.quantity),0), COALESCE(SUM(oi.quantity * oi.priceAtOrder),0)
            FROM Order o JOIN o.items oi
            WHERE o.restaurant.restaurantId = :rid
              AND o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.createdAt >= :from AND o.createdAt < :to
            GROUP BY oi.foodName
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> topFoodsByRestaurantBetween(@Param("rid") Long rid,
                                               @Param("from") java.time.LocalDateTime from,
                                               @Param("to") java.time.LocalDateTime to,
                                               Pageable pageable);

    // ─── Admin (toàn hệ thống) ───

    /** Tài chính đơn hoàn tất toàn sàn: {SUM(total), SUM(subtotal), SUM(shipping), COUNT}. */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount),0), COALESCE(SUM(o.subtotalAmount),0),
                   COALESCE(SUM(o.shippingFee),0), COUNT(o)
            FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    List<Object[]> financeCompletedSystemBetween(@Param("from") java.time.LocalDateTime from,
                                                 @Param("to") java.time.LocalDateTime to);

    @Query("""
            SELECT o.orderStatus, COUNT(o), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.orderStatus
            """)
    List<Object[]> statusDistSystemBetween(@Param("from") java.time.LocalDateTime from,
                                           @Param("to") java.time.LocalDateTime to);

    @Query("""
            SELECT o.paymentStatus, COUNT(o), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.paymentStatus
            """)
    List<Object[]> paymentDistSystemBetween(@Param("from") java.time.LocalDateTime from,
                                            @Param("to") java.time.LocalDateTime to);

    /** Chuỗi ngày toàn sàn: {yyyy-MM-dd, gtv(hoàn tất), subtotal(hoàn tất), count(mọi đơn)}. */
    @Query(value = """
            SELECT DATE(created_at) AS d,
                   COALESCE(SUM(CASE WHEN order_status='COMPLETED' AND payment_status<>'REFUNDED'
                                     THEN total_amount ELSE 0 END),0) AS gtv,
                   COALESCE(SUM(CASE WHEN order_status='COMPLETED' AND payment_status<>'REFUNDED'
                                     THEN subtotal_amount ELSE 0 END),0) AS sub,
                   COUNT(*) AS cnt
            FROM orders
            WHERE created_at >= :from AND created_at < :to
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<Object[]> dailySystemBetween(@Param("from") java.time.LocalDateTime from,
                                      @Param("to") java.time.LocalDateTime to);

    @Query("""
            SELECT COUNT(DISTINCT o.customer.userId) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            """)
    long countDistinctCustomersSystemBetween(@Param("from") java.time.LocalDateTime from,
                                             @Param("to") java.time.LocalDateTime to);

    /** Top quán theo doanh thu món trong kỳ (đơn hoàn tất): {restaurantName, COUNT, SUM(subtotal), SUM(total)}. */
    @Query("""
            SELECT o.restaurant.restaurantName, COUNT(o),
                   COALESCE(SUM(o.subtotalAmount),0), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.restaurant.restaurantName
            ORDER BY SUM(o.subtotalAmount) DESC
            """)
    List<Object[]> topRestaurantsSystemBetween(@Param("from") java.time.LocalDateTime from,
                                               @Param("to") java.time.LocalDateTime to,
                                               Pageable pageable);

    // Tìm danh sách đơn hàng theo trạng thái và thời gian tạo trước một mốc thời gian cutoffTime
    List<Order> findByOrderStatusAndCreatedAtBefore(OrderStatus orderStatus, LocalDateTime cutoffTime);

    // ─────────────── VOUCHER ANALYTICS (đơn hoàn tất có gắn voucher) ───────────────

    /** Tài chính đơn hoàn tất CÓ voucher trong [from,to): {count, SUM(discount), SUM(total)}. */
    @Query("""
            SELECT COUNT(o), COALESCE(SUM(o.discountAmount),0), COALESCE(SUM(o.totalAmount),0)
            FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.voucher IS NOT NULL
              AND o.createdAt >= :from AND o.createdAt < :to
            """)
    List<Object[]> voucherFinanceBetween(@Param("from") java.time.LocalDateTime from,
                                         @Param("to") java.time.LocalDateTime to);

    /** Top voucher theo lượt dùng trong [from,to): {code, name, uses, SUM(discount)}. */
    @Query("""
            SELECT o.voucher.code, o.voucher.name, COUNT(o), COALESCE(SUM(o.discountAmount),0)
            FROM Order o
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND o.paymentStatus != org.example.datn.domain.enums.PaymentStatus.REFUNDED
              AND o.voucher IS NOT NULL
              AND o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.voucher.voucherId, o.voucher.code, o.voucher.name
            ORDER BY COUNT(o) DESC
            """)
    List<Object[]> topVouchersBetween(@Param("from") java.time.LocalDateTime from,
                                      @Param("to") java.time.LocalDateTime to,
                                      Pageable pageable);

    /** Lượt dùng voucher theo NGÀY trong [from,to): {yyyy-MM-dd, uses, SUM(discount)}. Native cho DATE(). */
    @Query(value = """
            SELECT DATE(created_at) AS d, COUNT(*) AS uses, COALESCE(SUM(discount_amount),0) AS disc
            FROM orders
            WHERE order_status = 'COMPLETED' AND payment_status <> 'REFUNDED'
              AND voucher_id IS NOT NULL
              AND created_at >= :from AND created_at < :to
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """, nativeQuery = true)
    List<Object[]> dailyVoucherUsageBetween(@Param("from") java.time.LocalDateTime from,
                                            @Param("to") java.time.LocalDateTime to);
}

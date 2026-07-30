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
}

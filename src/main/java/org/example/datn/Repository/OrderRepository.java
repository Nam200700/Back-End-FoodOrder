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

    Page<Order> findByCustomerUserId(Long customerId, Pageable pageable);

    Page<Order> findByCustomerUserIdAndOrderStatus(Long customerId, OrderStatus status, Pageable pageable);

    Page<Order> findByRestaurantRestaurantId(Long restaurantId, Pageable pageable);

    Page<Order> findByRestaurantRestaurantIdAndOrderStatus(Long restaurantId, OrderStatus status, Pageable pageable);

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
}

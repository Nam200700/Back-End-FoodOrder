package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.CreateOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.DTO.response.shipping.ShippingCalculateResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.OrderStatusException;
import org.example.datn.Repository.*;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.NotificationType;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.mapper.OrderMapper;
import org.example.datn.security.OwnershipGuard;
import org.example.datn.util.HaversineCalculator;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.datn.domain.enums.OrderStatus.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final OwnershipGuard ownershipGuard;
    private final DeliveryService deliveryService;
    private final TransactionService transactionService;
    private final PaymentService paymentService;
    private final ReviewRepository reviewRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final ShipperRepository shipperRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final ShippingService shippingService;
    private final DeliveryRepository deliveryRepository;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY_FOR_PICKUP),
            READY_FOR_PICKUP, Set.of(PICKED_UP),
            PICKED_UP, Set.of(DELIVERING),
            DELIVERING, Set.of(COMPLETED)
    );

    // ─── Customer ────────────────────────────────────────────
    @Transactional
    public List<OrderResponse> createOrder(Long customerId, CreateOrderRequest req) {
        User customer = userRepository.getReferenceById(customerId);
        List<Order> savedOrders = req.getRestaurantId().stream().map(restaurantId -> {
            Cart cart = cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurantId)
                    .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));
            if (cart.getItems().isEmpty()) {
                throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
            }
            Order order = Order.builder()
                    .customer(customer)
                    .restaurant(cart.getRestaurant())
                    .deliveryAddress(req.getDeliveryAddress())
                    .deliveryLat(req.getDeliveryLat())
                    .deliveryLng(req.getDeliveryLng())
                    .paymentMethod(req.getPaymentMethod())
                    .discountAmount(BigDecimal.ZERO)
                    .orderStatus(PENDING)
                    .note(req.getNote())
                    .build();

            // Snapshot price + name at order time.
            List<OrderItem> items = cart.getItems().stream().map(ci -> {
                Food food = ci.getFood();
                if (!Boolean.TRUE.equals(food.getStatus())) {
                    throw new AppException(ErrorCode.FOOD_NOT_FOUND, "Món " + food.getFoodName() + " đã ngừng bán.");
                }
                if (!Boolean.TRUE.equals(food.getIsAvailable())) {
                    throw new AppException(ErrorCode.FOOD_NOT_FOUND, "Món " + food.getFoodName() + " hiện đã tạm hết hàng.");
                }

                return OrderItem.builder()
                        .order(order)
                        .food(food)
                        .foodName(food.getFoodName())
                        .quantity(ci.getQuantity())
                        .priceAtOrder(food.getPrice())
                        .note(ci.getNote())
                        .build();
            }).toList();

            order.getItems().addAll(items);

            double distance = shippingService.getDistanceKm(
                    cart.getRestaurant().getLatitude().doubleValue(), cart.getRestaurant().getLongitude().doubleValue(),
                    req.getDeliveryLat().doubleValue(), req.getDeliveryLng().doubleValue()
            );

            long shippingFee = ShippingFeeCalculator.calculate(distance);
            BigDecimal shippingFeeBd = BigDecimal.valueOf(shippingFee);
            order.setShippingFee(shippingFeeBd);
            BigDecimal subtotal = items.stream()
                    .map(i -> i.getPriceAtOrder().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotalAmount(subtotal);
            order.setTotalAmount(subtotal.add(shippingFeeBd));

            Order saved = orderRepository.save(order);
            cartRepository.delete(cart);
            paymentService.createForOrder(saved);

            notificationService.notifyUser(saved.getRestaurant().getOwner().getUserId(),
                    NotificationType.ORDER_NEW, saved.getOrderId());
            webSocketService.broadcastOrderStatus(saved);
            return saved;
        }).toList();
        return savedOrders.stream().map(orderMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(Long customerId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId, pageable)
                : orderRepository.findByCustomerUserIdAndOrderStatusOrderByCreatedAtDesc(customerId, status, pageable);
        return page.map(orderMapper::toResponse).map(this::enrichOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(Long customerId, Long orderId) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkOrderOwner(order, customerId);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse cancelOrderByCustomer(Long customerId, Long orderId, String reason) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkOrderOwner(order, customerId);
        validateTransition(order.getOrderStatus(), CANCELLED);

        order.setOrderStatus(CANCELLED);
        order.setCancelReason(reason);
        orderRepository.save(order);

        notificationService.notifyUser(order.getRestaurant().getOwner().getUserId(),
                NotificationType.ORDER_CANCELLED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest req, Long currentUserId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        OrderStatus st = order.getOrderStatus();

        // 1) Không cho hủy đơn đã kết thúc
        if (st == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (st == OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_COMPLETED);
        }

        // 2) Kiểm tra quyền theo vai trò + sở hữu
        Role role = current.getRole();
        boolean earlyStage = (st == OrderStatus.PENDING || st == OrderStatus.CONFIRMED);

        switch (role) {
            case CUSTOMER -> {
                if (!order.getCustomer().getUserId().equals(current.getUserId())) {
                    throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn này");
                }
                if (!earlyStage) {
                    throw new AppException(ErrorCode.ORDER_CANCEL_STAGE_INVALID);
                }
            }
            case OWNER -> {
                if (!order.getRestaurant().getOwner().getUserId().equals(current.getUserId())) {
                    throw new AppException(ErrorCode.FORBIDDEN, "Đơn này không thuộc quán của bạn");
                }
                if (!earlyStage) {
                    throw new AppException(ErrorCode.ORDER_CANCEL_STAGE_INVALID, "Đơn đã qua giai đoạn cho phép hủy");
                }
            }
            case ADMIN -> { /* admin hủy được mọi trạng thái (trừ COMPLETED và CANCELLED) */ }
            default -> throw new AppException(ErrorCode.FORBIDDEN, "Vai trò không được phép hủy đơn");
        }

        // 3) Cập nhật đơn
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(current);
        order.setCancelReason(req.getReason().trim());

        // 4) Xử lý thanh toán
        Payment payment = paymentRepository.findByOrderOrderId(orderId).orElse(null);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            // ADMIN hủy đơn đã thu tiền -> hoàn tiền + đảo earning
            refundService.refundOrder(order, payment);
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        // 5) Giảm active_delivery của shipper nếu có
        if (order.getShipper() != null) {
            shipperRepository.findByUserUserId(order.getShipper().getUserId())
                    .ifPresent(s -> {
                        s.setActiveDelivery(Math.max(0, s.getActiveDelivery() - 1));
                        shipperRepository.save(s);
                    });
        }

        orderRepository.save(order);

        // 6) Gửi thông báo ORDER_CANCELLED
        notificationService.notifyOrderCancelled(order, role);

        // 7) Broadcast socket
        webSocketService.broadcastOrderStatus(order);

        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    // ─── Merchant ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMerchantOrders(Long merchantId, Long restaurantId, OrderStatus status, Pageable pageable) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);
        Page<Order> page;
        if (status == null) {
            page = orderRepository.findByRestaurantRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
        } else {
            page = orderRepository.findByRestaurantRestaurantIdAndOrderStatusOrderByCreatedAtDesc(restaurantId, status, pageable);
        }
        return page.map(orderMapper::toResponse).map(this::enrichOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMerchantOrder(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse confirmOrder(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        validateTransition(order.getOrderStatus(), CONFIRMED);
        order.setOrderStatus(CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_CONFIRMED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse rejectOrder(Long merchantId, Long orderId, String reason) {
        Order order = getOrderForMerchant(merchantId, orderId);
        validateTransition(order.getOrderStatus(), CANCELLED);
        order.setOrderStatus(CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledBy(order.getRestaurant().getOwner());
        order.setPaymentStatus(PaymentStatus.FAILED);
        orderRepository.save(order);

        // Refund if already paid (e.g. VNPay prepaid order).
        //paymentService.refundIfPaid(order);
        Payment payment = paymentRepository.findByOrderOrderId(orderId).orElse(null);
        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_CANCELLED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse markPreparing(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        validateTransition(order.getOrderStatus(), PREPARING);
        order.setOrderStatus(PREPARING);
        order.setPreparingAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_PREPARING, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse markReadyForPickup(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        validateTransition(order.getOrderStatus(), READY_FOR_PICKUP);
        order.setOrderStatus(READY_FOR_PICKUP);
        order.setReadyAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationService.broadcastToShippers(order.getOrderId(), NotificationType.ORDER_READY_PICKUP);
        webSocketService.broadcastOrderStatus(order);
        webSocketService.broadcastAvailableOrder(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    // ─── Shipper ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrderResponse> getAvailableOrders() {
        return orderRepository.findAvailableOrders().stream()
                .map(orderMapper::toResponse)
                .map(this::enrichOrderResponse)
                .toList();
    }

    /** SERIALIZABLE so two shippers cannot accept the same order. */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OrderResponse acceptOrder(Long shipperId, Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId, ErrorCode.ORDER_NOT_FOUND);
        Shipper shipper = shipperRepository.findByUserUserId(shipperId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (order.getShipper() != null) {
            throw new AppException(ErrorCode.ORDER_ALREADY_TAKEN);
        }
        if (order.getOrderStatus() != READY_FOR_PICKUP) {
            throw new AppException(ErrorCode.ORDER_NOT_READY_FOR_PICKUP);
        }

        if (!Boolean.TRUE.equals(shipper.getIsOnline())) {
            throw new AppException(ErrorCode.SHIPPER_OFFLINE);
        }
        if (shipper.getActiveDelivery() > 0) {
            throw new AppException(ErrorCode.SHIPPER_BUSY);
        }

        order.setShipper(userRepository.getReferenceById(shipperId));
        orderRepository.save(order);

        shipper.setActiveDelivery(shipper.getActiveDelivery() + 1);
        shipperRepository.save(shipper);

        deliveryService.createDelivery(order, shipperId);

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.SHIPPER_ASSIGNED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse markPickedUp(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), PICKED_UP);
        order.setOrderStatus(PICKED_UP);
        order.setPickedUpAt(LocalDateTime.now());
        orderRepository.save(order);
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse markDelivering(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), DELIVERING);
        order.setOrderStatus(DELIVERING);
        orderRepository.save(order);
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    public OrderResponse markCompleted(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), COMPLETED);
        order.setOrderStatus(COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Cập nhật activeDelivery và totalDelivery cho tài xế
        Shipper shipper = shipperRepository.findByUserUserId(shipperId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (shipper.getActiveDelivery() > 0) {
            shipper.setActiveDelivery(shipper.getActiveDelivery() - 1);
        } else {
            shipper.setActiveDelivery(0);
        }
        shipper.setTotalDelivery(shipper.getTotalDelivery() + 1);
        shipperRepository.save(shipper);

        // COD orders are settled on delivery.
        paymentService.markCodPaidOnCompletion(order);
        deliveryService.completeDelivery(order);
        transactionService.recordOrderTransactions(order);

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_COMPLETED, order.getOrderId());
        notificationService.notifyUser(order.getRestaurant().getOwner().getUserId(),
                NotificationType.ORDER_COMPLETED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    // ─── Helpers ─────────────────────────────────────────────
    private Order loadWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private Order getOrderForMerchant(Long merchantId, Long orderId) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkRestaurantOwner(order.getRestaurant(), merchantId);
        return order;
    }

    private Order getOrderForShipper(Long shipperId, Long orderId) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkShipperAssigned(order, shipperId);
        return order;
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        if (!VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new OrderStatusException("Không thể chuyển từ " + current + " sang " + next);
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getShipperOrders(Long shipperId, OrderStatus status, Pageable pageable) {
        Page<Delivery> deliveryPage = deliveryRepository.findByShipperIdAndOrderStatus(shipperId, status, pageable);
        return deliveryPage.map(delivery -> {
            Order order = delivery.getOrder();
            OrderResponse response = orderMapper.toResponse(order);
            return enrichOrderResponse(response);
        });
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse)
                .map(this::enrichOrderResponse);
    }

    private OrderResponse enrichOrderResponse(OrderResponse response) {
        if (response == null) return null;
        boolean reviewed = reviewRepository.existsByOrderOrderId(response.getOrderId());
        response.setReviewed(reviewed);
        if (reviewed) {
            reviewRepository.findByOrderOrderId(response.getOrderId())
                    .ifPresent(r -> {
                        response.setRestaurantRating(r.getRestaurantRating());
                        response.setShipperRating(r.getShipperRating());
                    });
        }
        if (response.getShipperId() != null) {
            shipperRegisterRepository.findByUserUserId(response.getShipperId()).ifPresent(reg -> {
                response.setShipperVehicleType(reg.getVehicleType() != null ? reg.getVehicleType().name() : null);
                response.setShipperLicensePlate(reg.getLicensePlate());
            });
        }
        return response;
    }
}
package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.CreateOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.OrderStatusException;
import org.example.datn.Repository.*;
import org.example.datn.Service.RefundService;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.NotificationType;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.mapper.OrderMapper;
import org.example.datn.security.OwnershipGuard;
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
    public OrderResponse createOrder(Long customerId, CreateOrderRequest req) {
        Cart cart = cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, req.getRestaurantId())
                .orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));
        if (cart.getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        Order order = Order.builder()
                .customer(userRepository.getReferenceById(customerId))
                .restaurant(cart.getRestaurant())
                .deliveryAddress(req.getDeliveryAddress())
                .deliveryLat(req.getDeliveryLat())
                .deliveryLng(req.getDeliveryLng())
                .paymentMethod(req.getPaymentMethod())
                .shippingFee(req.getShippingFee())
                .discountAmount(BigDecimal.ZERO)
                .orderStatus(PENDING)
                .note(req.getNote())
                .build();

        // Snapshot price + name at order time.
        List<OrderItem> items = cart.getItems().stream().map(ci -> OrderItem.builder()
                .order(order)
                .food(ci.getFood())
                .foodName(ci.getFood().getFoodName())
                .quantity(ci.getQuantity())
                .priceAtOrder(ci.getFood().getPrice())
                .note(ci.getNote())
                .build()).toList();
        order.getItems().addAll(items);

        BigDecimal subtotal = items.stream()
                .map(i -> i.getPriceAtOrder().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalAmount(subtotal);
        order.setTotalAmount(subtotal.add(req.getShippingFee()));

        Order saved = orderRepository.save(order);
        cartRepository.delete(cart);
        paymentService.createForOrder(saved);

        notificationService.notifyUser(saved.getRestaurant().getOwner().getUserId(),
                NotificationType.ORDER_NEW, saved.getOrderId());
        webSocketService.broadcastOrderStatus(saved);
        return enrichOrderResponse(orderMapper.toResponse(saved));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(Long customerId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByCustomerUserId(customerId, pageable)
                : orderRepository.findByCustomerUserIdAndOrderStatus(customerId, status, pageable);
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
            page = orderRepository.findByRestaurantRestaurantId(restaurantId, pageable);
        } else if (status == OrderStatus.PREPARING) {
            page = orderRepository.findByRestaurantRestaurantIdAndOrderStatusIn(
                    restaurantId,
                    List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING),
                    pageable
            );
        } else if (status == OrderStatus.DELIVERING) {
            page = orderRepository.findByRestaurantRestaurantIdAndOrderStatusIn(
                    restaurantId,
                    List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP, OrderStatus.DELIVERING),
                    pageable
            );
        } else {
            page = orderRepository.findByRestaurantRestaurantIdAndOrderStatus(restaurantId, status, pageable);
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
        orderRepository.save(order);

        // Refund if already paid (e.g. VNPay prepaid order).
        paymentService.refundIfPaid(order);

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
        if (order.getShipper() != null) {
            throw new AppException(ErrorCode.ORDER_ALREADY_TAKEN);
        }
        if (order.getOrderStatus() != READY_FOR_PICKUP) {
            throw new OrderStatusException("Đơn chưa sẵn sàng để nhận");
        }
        order.setShipper(userRepository.getReferenceById(shipperId));
        orderRepository.save(order);

        // Cập nhật activeDelivery cho tài xế
        Shipper shipper = shipperRepository.findByUserUserId(shipperId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
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
    public Page<OrderResponse> getShipperOrders(Long shipperId, Pageable pageable) {
        return orderRepository.findByShipperUserId(shipperId, pageable)
                .map(orderMapper::toResponse)
                .map(this::enrichOrderResponse);
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

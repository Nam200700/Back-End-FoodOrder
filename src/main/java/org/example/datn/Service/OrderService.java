package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.annotation.EvictStatsCaches;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.CreateOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.OrderStatusException;
import org.example.datn.Repository.*;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.NotificationType;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.mapper.OrderMapper;
import org.example.datn.security.OwnershipGuard;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.datn.domain.enums.OrderStatus.*;

@Slf4j
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
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PENDING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY_FOR_PICKUP),
            READY_FOR_PICKUP, Set.of(PICKED_UP),
            PICKED_UP, Set.of(DELIVERING),
            DELIVERING, Set.of(COMPLETED)
    );

    @Scheduled(fixedRate = 30000)
    public void autoCancelExpiredPendingOrders() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);

        // 1. Tìm các đơn PENDING tạo trước (quá 5 phút)
        List<Order> expiredOrders = orderRepository.findByOrderStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoffTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        // 2. Tìm Voucher đền bù (ORDER_CANCELLED)
        Voucher compensationVoucher = voucherRepository.findActiveVoucherByIssueType(VoucherIssueType.ORDER_CANCELLED)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        for (Order order : expiredOrders) {
            try {
                cancelAndCompensateOrder(order, compensationVoucher);
            } catch (Exception e) {
                log.error("Lỗi khi tự động hủy đơn hàng ID: {}", order.getOrderId(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAndCompensateOrder(Order order, Voucher compensationVoucher) {
        // 1. Cập nhật trạng thái đơn hàng sang CANCELLED
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Hệ thống tự động hủy do quán không xác nhận đơn trong vòng 5 phút");
        order.setPaymentStatus(PaymentStatus.FAILED);
        voucherService.refundVoucher(order);
        order.setUserVoucher(null);
        orderRepository.save(order);
        paymentService.markPaymentFailed(order);

        User customer = order.getCustomer();

        // 2. Phát Voucher đền bù cho khách hàng
        if (compensationVoucher != null && customer != null) {
            LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);

            UserVoucher userVoucher = UserVoucher.builder()
                    .user(customer)
                    .voucher(compensationVoucher)
                    .used(false)
                    .receivedAt(LocalDateTime.now())
                    .expiredAt(expiredAt)
                    .build();
            userVoucherRepository.save(userVoucher);
        }

        // 3. Gửi thông báo
        try {
            if (order.getRestaurant() != null && order.getRestaurant().getOwner() != null) {
                notificationService.notifyUser(
                        order.getRestaurant().getOwner().getUserId(),
                        NotificationType.ORDER_CANCELLED,
                        order.getOrderId()
                );
            }
            if (customer != null) {
                notificationService.notifyUser(
                        customer.getUserId(),
                        NotificationType.ORDER_CANCELLED,
                        order.getOrderId()
                );
            }

            webSocketService.broadcastOrderStatus(order);
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo/websocket cho đơn hàng tự hủy ID: {}", order.getOrderId(), e);
        }
    }

    // ─── Customer ────────────────────────────────────────────
    @Transactional
    @EvictStatsCaches
    public List<OrderResponse> createOrder(Long customerId, CreateOrderRequest req) {
        User customer = userRepository.getReferenceById(customerId);

        List<Order> ordersToSave = new ArrayList<>();
        List<Cart> cartsToDelete = new ArrayList<>();
        List<UserVoucher> vouchersToUpdate = new ArrayList<>();
        List<Voucher> rawVouchersToUpdate = new ArrayList<>();

        // 1. Validate và chuẩn bị dữ liệu cho tất cả các quán trước (Fail-fast)
        for (Long restaurantId : req.getRestaurantId()) {
            Cart cart = cartRepository.findByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurantId)
                    .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

            if (cart.getItems().isEmpty()) {
                throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
            }

            // Xử lý Voucher của quán này
            Long userVoucherId = (req.getRestaurantVouchers() != null) ? req.getRestaurantVouchers().get(restaurantId) : null;
            UserVoucher userVoucher = null;
            Voucher voucher = null;

            if (userVoucherId != null) {
                userVoucher = userVoucherRepository.findById(userVoucherId)
                        .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

                if (!userVoucher.getUser().getUserId().equals(customerId)) {
                    throw new AppException(ErrorCode.VOUCHER_NOT_OWNED);
                }
                if (Boolean.TRUE.equals(userVoucher.getUsed())) {
                    throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);
                }
                if (userVoucher.getExpiredAt() != null && userVoucher.getExpiredAt().isBefore(LocalDateTime.now())) {
                    throw new AppException(ErrorCode.VOUCHER_EXPIRED);
                }
                voucher = userVoucher.getVoucher();
            }

            // Tạo và tính toán chi tiết cho đơn hàng của quán
            Order order = buildOrderEntity(customer, cart, req, userVoucher);
            ordersToSave.add(order);
            cartsToDelete.add(cart);

            // Chuẩn bị dữ liệu cập nhật Voucher nếu có
            if (userVoucher != null) {
                userVoucher.setUsed(true);
                userVoucher.setUsedAt(LocalDateTime.now());
                vouchersToUpdate.add(userVoucher);

                if (voucher != null) {
                    voucher.setUsedQuantity((voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0) + 1);
                    rawVouchersToUpdate.add(voucher);
                }
            }
        }

        List<Order> savedOrders = orderRepository.saveAll(ordersToSave);

        // Lưu các thay đổi của Voucher
        if (!vouchersToUpdate.isEmpty()) {
            userVoucherRepository.saveAll(vouchersToUpdate);
        }
        if (!rawVouchersToUpdate.isEmpty()) {
            voucherRepository.saveAll(rawVouchersToUpdate);
        }

        // Tạo bản ghi Payment
        for (Order saved : savedOrders) {
            paymentService.createForOrder(saved);
        }

        cartRepository.deleteAll(cartsToDelete);

        // 3. Gửi thông báo và WebSocket
        for (Order saved : savedOrders) {
            try {
                if (saved.getRestaurant() != null && saved.getRestaurant().getOwner() != null) {
                    notificationService.notifyUser(
                            saved.getRestaurant().getOwner().getUserId(),
                            NotificationType.ORDER_NEW,
                            saved.getOrderId()
                    );
                }
                webSocketService.broadcastOrderStatus(saved);
            } catch (Exception e) {
                log.error("Lỗi khi gửi thông báo/websocket cho đơn hàng mới ID: {}", saved.getOrderId(), e);
            }
        }

        return savedOrders.stream().map(orderMapper::toResponse).toList();
    }

    private Order buildOrderEntity(User customer, Cart cart, CreateOrderRequest req, UserVoucher userVoucher) {
        Restaurant restaurant = cart.getRestaurant();

        // =========================================================================
        // 1. KIỂM TRA KHUNG GIỜ HOẠT ĐỘNG CỦA QUÁN (opensAt - closesAt)
        // =========================================================================
        if (restaurant.getOpensAt() != null && restaurant.getClosesAt() != null) {
            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.LocalTime opensAt = restaurant.getOpensAt();
            java.time.LocalTime closesAt = restaurant.getClosesAt();

            boolean isOpen;
            if (opensAt.isBefore(closesAt)) {
                // Giờ mở cửa bình thường trong ngày (VD: 07:00 -> 22:00)
                isOpen = !now.isBefore(opensAt) && !now.isAfter(closesAt);
            } else {
                // Giờ mở cửa qua đêm (VD: 18:00 -> 02:00 sáng hôm sau)
                isOpen = !now.isBefore(opensAt) || !now.isAfter(closesAt);
            }

            if (!isOpen) {
                String timeMsg = String.format("Quán '%s' hiện đã đóng cửa (Giờ mở cửa: %s - %s). Vui lòng quay lại sau!",
                        restaurant.getRestaurantName(),
                        opensAt.toString(),
                        closesAt.toString());
                throw new AppException(ErrorCode.RESTAURANT_CLOSED, timeMsg);
            }
        }

        Order order = Order.builder()
                .customer(customer)
                .restaurant(restaurant)
                .deliveryAddress(req.getDeliveryAddress())
                .deliveryLat(req.getDeliveryLat())
                .deliveryLng(req.getDeliveryLng())
                .paymentMethod(req.getPaymentMethod())
                .discountAmount(BigDecimal.ZERO)
                .orderStatus(PENDING)
                .note(req.getNote())
                .userVoucher(userVoucher)
                .build();

        // Snapshot món ăn
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

        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa cập nhật tọa độ vị trí.");
        }
        if (req.getDeliveryLat() == null || req.getDeliveryLng() == null) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND, "Vui lòng chọn địa chỉ giao hàng hợp lệ.");
        }

        double distance = shippingService.getDistanceKm(
                restaurant.getLatitude().doubleValue(), restaurant.getLongitude().doubleValue(),
                req.getDeliveryLat().doubleValue(), req.getDeliveryLng().doubleValue()
        );

        if (distance > 10.0) {
            String distMsg = String.format("Quán '%s' cách bạn %.1f km. Hệ thống chỉ hỗ trợ đặt quán trong phạm vi 10 km!",
                    restaurant.getRestaurantName(), distance);
            throw new AppException(ErrorCode.DISTANCE_TOO_FAR, distMsg);
        }

        long shippingFee = ShippingFeeCalculator.calculate(distance);
        BigDecimal shippingFeeBd = BigDecimal.valueOf(shippingFee);
        order.setShippingFee(shippingFeeBd);

        // Tính tiền món ăn (Subtotal)
        BigDecimal subtotal = items.stream()
                .map(i -> i.getPriceAtOrder().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalAmount(subtotal);

        BigDecimal totalBeforeDiscount = subtotal.add(shippingFeeBd);

        Voucher voucher = (userVoucher != null) ? userVoucher.getVoucher() : null;
        // Tính giá trị giảm giá từ Voucher
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (voucher != null) {
            switch (voucher.getDiscountType()) {
                case FIXED -> discountAmount = voucher.getDiscountValue();
                case PERCENT -> discountAmount = subtotal.multiply(voucher.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                case FREESHIP -> discountAmount = shippingFeeBd;
            }
        }

        if (discountAmount.compareTo(totalBeforeDiscount) > 0) {
            throw new AppException(ErrorCode.VOUCHER_DISCOUNT_EXCEEDED);
        }

        order.setDiscountAmount(discountAmount);

        BigDecimal totalAmount = totalBeforeDiscount.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        order.setTotalAmount(totalAmount);

        return order;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(Long customerId, OrderStatus status, String keyword, Pageable pageable) {
        Page<Order> orderPage = orderRepository.searchCustomerOrders(customerId, status, keyword, pageable);
        return orderPage.map(orderMapper::toResponse).map(this::enrichOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(Long customerId, Long orderId) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkOrderOwner(order, customerId);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    @EvictStatsCaches
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
    @EvictStatsCaches
    public OrderResponse cancelOrder(Long orderId, CancelOrderRequest req, Long currentUserId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        OrderStatus st = order.getOrderStatus();

        if (st == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (st == OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_COMPLETED);
        }

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
            case ADMIN -> { }
            default -> throw new AppException(ErrorCode.FORBIDDEN, "Vai trò không được phép hủy đơn");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(current);
        order.setCancelReason(req.getReason().trim());

        voucherService.refundVoucher(order);
        order.setUserVoucher(null);

        Payment payment = paymentRepository.findByOrderOrderId(orderId).orElse(null);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            refundService.refundOrder(order, payment);
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        if (order.getShipper() != null) {
            shipperRepository.findByUserUserId(order.getShipper().getUserId())
                    .ifPresent(s -> {
                        s.setActiveDelivery(Math.max(0, s.getActiveDelivery() - 1));
                        shipperRepository.save(s);
                    });
        }

        orderRepository.save(order);
        notificationService.notifyOrderCancelled(order, role);
        webSocketService.broadcastOrderStatus(order);

        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    // ─── Merchant ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMerchantOrders(Long merchantId, Long restaurantId, OrderStatus status, String keyword, Pageable pageable) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);
        Page<Order> orderPage = orderRepository.searchMerchantOrders(restaurantId, status, keyword, pageable);
        return orderPage.map(orderMapper::toResponse).map(this::enrichOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMerchantOrder(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    @EvictStatsCaches
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
    @EvictStatsCaches
    public OrderResponse rejectOrder(Long merchantId, Long orderId, String reason) {
        Order order = getOrderForMerchant(merchantId, orderId);
        validateTransition(order.getOrderStatus(), CANCELLED);
        order.setOrderStatus(CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledBy(order.getRestaurant().getOwner());
        order.setPaymentStatus(PaymentStatus.FAILED);
        voucherService.refundVoucher(order);
        order.setUserVoucher(null);
        orderRepository.save(order);

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
    @EvictStatsCaches
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
    @EvictStatsCaches
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
                // Bảo vệ PII: KHÔNG lộ SĐT khách khi đơn còn ở pool (chưa ai nhận).
                // Sau khi shipper nhận đơn, các endpoint khác mới trả đủ số điện thoại để liên hệ giao hàng.
                .map(r -> { r.setCustomerPhone(null); return r; })
                .toList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @EvictStatsCaches
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
    @EvictStatsCaches
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
    @EvictStatsCaches
    public OrderResponse markDelivering(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), DELIVERING);
        order.setOrderStatus(DELIVERING);
        orderRepository.save(order);
        webSocketService.broadcastOrderStatus(order);
        return enrichOrderResponse(orderMapper.toResponse(order));
    }

    @Transactional
    @EvictStatsCaches
    public OrderResponse markCompleted(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), COMPLETED);
        order.setOrderStatus(COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        Shipper shipper = shipperRepository.findByUserUserId(shipperId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (shipper.getActiveDelivery() > 0) {
            shipper.setActiveDelivery(shipper.getActiveDelivery() - 1);
        } else {
            shipper.setActiveDelivery(0);
        }
        shipper.setTotalDelivery(shipper.getTotalDelivery() + 1);
        shipperRepository.save(shipper);

        paymentService.markCodPaidOnCompletion(order);
        deliveryService.completeDelivery(order);
        transactionService.recordOrderTransactions(order);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationService.notifyUser(order.getCustomer().getUserId(),
                            NotificationType.ORDER_COMPLETED, order.getOrderId());
                    notificationService.notifyUser(order.getRestaurant().getOwner().getUserId(),
                            NotificationType.ORDER_COMPLETED, order.getOrderId());

                    webSocketService.broadcastOrderStatus(order);
                } catch (Exception e) {
                    log.error("Lỗi khi gửi thông báo/websocket sau khi commit đơn hàng hoàn thành ID: {}", order.getOrderId(), e);
                }
            }
        });

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
        orderRepository.findById(response.getOrderId()).ifPresent(order -> {
            if (order.getUserVoucher() != null && order.getUserVoucher().getVoucher() != null) {
                Voucher v = order.getUserVoucher().getVoucher();
                response.setVoucherCode(v.getCode());
            }
        });

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
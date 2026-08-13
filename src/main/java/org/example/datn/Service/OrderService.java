package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.annotation.EvictStatsCaches;
import org.example.datn.DTO.request.order.CancelOrderRequest;
import org.example.datn.DTO.request.order.CreateOrderRequest;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.DTO.response.order.MerchantOrderMonitorResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.OrderStatusException;
import org.example.datn.Repository.*;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.DeliveryStatus;
import org.example.datn.domain.enums.NotificationType;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.mapper.OrderMapper;
import org.example.datn.security.OwnershipGuard;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ReputationService reputationService;

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
        List<Order> expiredOrders = orderRepository.findByOrderStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoffTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        Voucher compensationVoucher = voucherRepository.findActiveVoucherByIssueType(VoucherIssueType.ORDER_CANCELLED)
                .orElse(null);

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

        // Chống bom hàng: khách có điểm uy tín quá thấp thì tạm không cho đặt đơn.
        if (customer.getReputationScore() != null
                && customer.getReputationScore() < ReputationService.CUSTOMER_ORDER_BLOCK_BELOW) {
            throw new AppException(ErrorCode.REPUTATION_TOO_LOW);
        }

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
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (voucher != null) {
            // 1. KIỂM TRA ĐIỀU KIỆN ĐƠN HÀNG TỐI THIỂU (minOrderAmount)
            if (voucher.getMinOrderAmount() != null && subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        String.format("Quán '%s': Tiền món (%,d VNĐ) chưa đạt giá trị tối thiểu %,d VNĐ để áp dụng mã '%s'!",
                                restaurant.getRestaurantName(),
                                subtotal.longValue(),
                                voucher.getMinOrderAmount().longValue(),
                                voucher.getCode()));
            }

            // 2. TÍNH TOÁN TIỀN GIẢM VÀ ÁP DỤNG TRẦN GIẢM TỐI ĐA (maxDiscountAmount)
            switch (voucher.getDiscountType()) {
                case FIXED -> discountAmount = voucher.getDiscountValue();
                case PERCENT -> {
                    discountAmount = subtotal.multiply(voucher.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                }
                case FREESHIP -> discountAmount = shippingFeeBd;
            }
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
        return new PageImpl<>(enrichPage(orderPage.getContent()), orderPage.getPageable(), orderPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(Long customerId, Long orderId) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkOrderOwner(order, customerId);
        return enrichOne(order, orderMapper.toResponse(order));
    }

    @Transactional
    @EvictStatsCaches
    public OrderResponse cancelOrderByCustomer(Long customerId, Long orderId, String reason) {
        Order order = loadWithItems(orderId);
        ownershipGuard.checkOrderOwner(order, customerId);
        OrderStatus prev = order.getOrderStatus();
        validateTransition(prev, CANCELLED);

        order.setOrderStatus(CANCELLED);
        order.setCancelReason(reason);
        orderRepository.save(order);

        // Khách hủy SAU khi quán đã xác nhận (CONFIRMED) → trừ uy tín; hủy lúc PENDING thì miễn phí.
        if (prev == CONFIRMED) {
            reputationService.penalize(order.getCustomer(), ReputationService.PENALTY_CUSTOMER_LATE_CANCEL);
        }

        notificationService.notifyUser(order.getRestaurant().getOwner().getUserId(),
                NotificationType.ORDER_CANCELLED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOne(order, orderMapper.toResponse(order));
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
                // Quán được hủy đơn SAU khi đã xác nhận: cho tới hết giai đoạn PREPARING
                // (hết nguyên liệu / quá tải) — trước khi đơn ra pool cho shipper.
                boolean ownerCancelable = (st == OrderStatus.PENDING || st == OrderStatus.CONFIRMED
                        || st == OrderStatus.PREPARING);
                if (!ownerCancelable) {
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

        // Điểm uy tín + đền bù theo vai trò gây hủy
        if (role == Role.CUSTOMER) {
            if (st == OrderStatus.CONFIRMED) {
                reputationService.penalize(order.getCustomer(), ReputationService.PENALTY_CUSTOMER_LATE_CANCEL);
            }
        } else if (role == Role.OWNER) {
            reputationService.penalize(order.getRestaurant().getOwner(), ReputationService.PENALTY_OWNER_CANCEL);
            bumpRestaurantCancelCount(order.getRestaurant());
            issueCompensationVoucher(order.getCustomer());   // không phải lỗi khách → đền voucher
        }

        notificationService.notifyOrderCancelled(order, role);
        webSocketService.broadcastOrderStatus(order);

        return enrichOne(order, orderMapper.toResponse(order));
    }

    // ─── Merchant ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMerchantOrders(Long merchantId, Long restaurantId, OrderStatus status, String keyword, Pageable pageable) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);
        Page<Order> orderPage = orderRepository.searchMerchantOrders(restaurantId, status, keyword, pageable);
        return new PageImpl<>(enrichPage(orderPage.getContent()), orderPage.getPageable(), orderPage.getTotalElements());
    }

    /**
     * Theo dõi đơn (đếm tab + đơn chờ mới) bằng 2 câu nhẹ thay cho việc FE nạp cả nghìn đơn/30s.
     * counts: GROUP BY trạng thái; pending: projection rút gọn đơn PENDING (id/tên khách/tổng tiền).
     */
    @Transactional(readOnly = true)
    public MerchantOrderMonitorResponse getMerchantOrderMonitor(Long merchantId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, merchantId);

        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (Object[] row : orderRepository.countMerchantOrdersByStatus(restaurantId)) {
            long c = ((Number) row[1]).longValue();
            counts.put(((OrderStatus) row[0]).name(), c);
            total += c;
        }
        counts.put("ALL", total);

        List<MerchantOrderMonitorResponse.PendingBrief> pending = orderRepository.findPendingBriefByRestaurant(restaurantId)
                .stream()
                .map(r -> MerchantOrderMonitorResponse.PendingBrief.builder()
                        .orderId(((Number) r[0]).longValue())
                        .customerName((String) r[1])
                        .totalAmount((BigDecimal) r[2])
                        .build())
                .toList();

        return MerchantOrderMonitorResponse.builder().counts(counts).pending(pending).build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMerchantOrder(Long merchantId, Long orderId) {
        Order order = getOrderForMerchant(merchantId, orderId);
        return enrichOne(order, orderMapper.toResponse(order));
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
        return enrichOne(order, orderMapper.toResponse(order));
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

        // Quán từ chối đơn mới → trừ uy tín quán (nhẹ) + tăng cancel_count + đền voucher khách.
        reputationService.penalize(order.getRestaurant().getOwner(), ReputationService.PENALTY_OWNER_REJECT);
        bumpRestaurantCancelCount(order.getRestaurant());
        issueCompensationVoucher(order.getCustomer());

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_CANCELLED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOne(order, orderMapper.toResponse(order));
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
        return enrichOne(order, orderMapper.toResponse(order));
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
        return enrichOne(order, orderMapper.toResponse(order));
    }

    // ─── Shipper ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OrderResponse> getAvailableOrders() {
        List<OrderResponse> res = enrichPage(orderRepository.findAvailableOrders());
        // Bảo vệ PII: KHÔNG lộ SĐT khách khi đơn còn ở pool (chưa ai nhận).
        // Sau khi shipper nhận đơn, các endpoint khác mới trả đủ số điện thoại để liên hệ giao hàng.
        res.forEach(r -> r.setCustomerPhone(null));
        return res;
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

        // Uy tín thấp → tạm không cho nhận đơn mới.
        User shipperUser = shipper.getUser();
        if (shipperUser.getReputationScore() != null
                && shipperUser.getReputationScore() < ReputationService.SHIPPER_ACCEPT_BLOCK_BELOW) {
            throw new AppException(ErrorCode.SHIPPER_REPUTATION_LOW);
        }

        order.setShipper(userRepository.getReferenceById(shipperId));
        orderRepository.save(order);

        shipper.setActiveDelivery(shipper.getActiveDelivery() + 1);
        shipperRepository.save(shipper);

        deliveryService.createDelivery(order, shipperId);

        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.SHIPPER_ASSIGNED, order.getOrderId());
        webSocketService.broadcastOrderStatus(order);
        return enrichOne(order, orderMapper.toResponse(order));
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
        return enrichOne(order, orderMapper.toResponse(order));
    }

    @Transactional
    @EvictStatsCaches
    public OrderResponse markDelivering(Long shipperId, Long orderId) {
        Order order = getOrderForShipper(shipperId, orderId);
        validateTransition(order.getOrderStatus(), DELIVERING);
        order.setOrderStatus(DELIVERING);
        orderRepository.save(order);
        webSocketService.broadcastOrderStatus(order);
        return enrichOne(order, orderMapper.toResponse(order));
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

        // Uy tín: hoàn tất đơn suôn sẻ → hồi điểm cho khách và shipper.
        reputationService.reward(order.getCustomer(), ReputationService.REWARD_ON_COMPLETE);
        reputationService.reward(shipper.getUser(), ReputationService.REWARD_ON_COMPLETE);
        // Loyalty: khách tích điểm theo tiền món (1 điểm / 10.000đ subtotal).
        accrueLoyalty(order.getCustomer(), order.getSubtotalAmount());

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

        return enrichOne(order, orderMapper.toResponse(order));
    }

    /**
     * Shipper BỎ ĐƠN đã nhận (đảo ngược acceptOrder): trả đơn về pool cho shipper khác,
     * trừ uy tín shipper + tăng cancel_count, đền voucher cho khách (không phải lỗi khách).
     */
    @Transactional
    @EvictStatsCaches
    public OrderResponse abandonOrder(Long shipperId, Long orderId, String reason) {
        Order order = getOrderForShipper(shipperId, orderId);   // đảm bảo đơn đang thuộc shipper này
        OrderStatus st = order.getOrderStatus();
        if (st == COMPLETED || st == CANCELLED) {
            throw new AppException(ErrorCode.ORDER_CANCEL_STAGE_INVALID, "Đơn đã kết thúc, không thể bỏ.");
        }

        User shipperUser = order.getShipper();

        // Trả đơn về pool: bỏ gán shipper, đưa trạng thái về READY_FOR_PICKUP.
        order.setShipper(null);
        order.setOrderStatus(READY_FOR_PICKUP);
        if (reason != null && !reason.isBlank()) {
            order.setCancelReason("Shipper bỏ đơn: " + reason.trim());
        }
        orderRepository.save(order);

        // Xoá bản ghi Delivery để shipper khác nhận lại được (uk_deliveries_order là unique theo order).
        deliveryService.cancelDelivery(order);

        // Shipper: -active_delivery, +cancel_count, trừ uy tín.
        Shipper shipper = shipperRepository.findByUserUserId(shipperId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        shipper.setActiveDelivery(Math.max(0, shipper.getActiveDelivery() - 1));
        shipper.setCancelCount(shipper.getCancelCount() + 1);
        shipperRepository.save(shipper);
        reputationService.penalize(shipperUser, ReputationService.PENALTY_SHIPPER_ABANDON);

        // Đền voucher cho khách.
        issueCompensationVoucher(order.getCustomer());

        // Thông báo + đẩy lại pool cho các shipper khác.
        notificationService.notifyUser(order.getCustomer().getUserId(),
                NotificationType.ORDER_CANCELLED, order.getOrderId());
        notificationService.broadcastToShippers(order.getOrderId(), NotificationType.ORDER_READY_PICKUP);
        webSocketService.broadcastOrderStatus(order);
        webSocketService.broadcastAvailableOrder(order);
        return enrichOne(order, orderMapper.toResponse(order));
    }

    // ─── Helpers ─────────────────────────────────────────────
    private Order loadWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    /** Cấp voucher đền bù (loại ORDER_CANCELLED đang active) cho khách khi hủy KHÔNG do lỗi khách. */
    private void issueCompensationVoucher(User customer) {
        if (customer == null) return;
        voucherRepository.findActiveVoucherByIssueType(VoucherIssueType.ORDER_CANCELLED).ifPresent(v -> {
            // uk_user_voucher UNIQUE(user_id, voucher_id) → chỉ cấp nếu khách chưa có voucher này.
            if (!userVoucherRepository.existsByUser_UserIdAndVoucher_VoucherId(customer.getUserId(), v.getVoucherId())) {
                userVoucherRepository.save(UserVoucher.builder()
                        .user(customer)
                        .voucher(v)
                        .used(false)
                        .receivedAt(LocalDateTime.now())
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build());
            }
        });
    }

    /** Tăng số đơn quán tự hủy/từ chối (để tính tỷ lệ hủy). */
    private void bumpRestaurantCancelCount(Restaurant restaurant) {
        if (restaurant == null) return;
        int cur = restaurant.getCancelCount() != null ? restaurant.getCancelCount() : 0;
        restaurant.setCancelCount(cur + 1);
        restaurantRepository.save(restaurant);
    }

    /** Cộng điểm loyalty cho khách: 1 điểm / 10.000đ tiền món (subtotal). */
    private void accrueLoyalty(User customer, BigDecimal subtotal) {
        if (customer == null || subtotal == null) return;
        int earned = subtotal.divide(BigDecimal.valueOf(10000), 0, java.math.RoundingMode.DOWN).intValue();
        if (earned <= 0) return;
        int cur = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
        customer.setLoyaltyPoints(cur + earned);
        userRepository.save(customer);
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
        List<Delivery> deliveries = deliveryPage.getContent();
        List<Order> orders = deliveries.stream().map(Delivery::getOrder).toList();
        List<OrderResponse> responses = enrichPage(orders);

        // Lịch sử shipper phản ánh trạng thái GIAO của shipper (không phải trạng thái đơn hiện tại):
        //  - delivery CANCELLED → shipper đã BỎ đơn → hiển thị "Đã hủy"
        //  - delivery COMPLETED → "Thành công"
        //  - ASSIGNED           → giữ trạng thái đơn (đang giao...)
        for (int i = 0; i < responses.size(); i++) {
            DeliveryStatus ds = deliveries.get(i).getStatus();
            if (ds == DeliveryStatus.CANCELLED) {
                responses.get(i).setOrderStatus(OrderStatus.CANCELLED);
            } else if (ds == DeliveryStatus.COMPLETED) {
                responses.get(i).setOrderStatus(OrderStatus.COMPLETED);
            }
        }
        return new PageImpl<>(responses, deliveryPage.getPageable(), deliveryPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        Page<Order> orderPage = orderRepository.findAll(pageable);
        return new PageImpl<>(enrichPage(orderPage.getContent()), orderPage.getPageable(), orderPage.getTotalElements());
    }

    /**
     * Enrich cho MỘT đơn (đã có entity trong tay → KHÔNG findById lại).
     * Voucher đọc thẳng từ entity; review + hồ sơ shipper mỗi loại 1 query.
     */
    private OrderResponse enrichOne(Order order, OrderResponse res) {
        if (res == null) return null;

        if (order.getUserVoucher() != null && order.getUserVoucher().getVoucher() != null) {
            res.setVoucherCode(order.getUserVoucher().getVoucher().getCode());
        }

        reviewRepository.findByOrderOrderId(order.getOrderId()).ifPresentOrElse(r -> {
            res.setReviewed(true);
            res.setRestaurantRating(r.getRestaurantRating());
            res.setShipperRating(r.getShipperRating());
        }, () -> res.setReviewed(false));

        if (res.getShipperId() != null) {
            shipperRegisterRepository.findByUserUserId(res.getShipperId()).ifPresent(reg -> {
                res.setShipperVehicleType(reg.getVehicleType() != null ? reg.getVehicleType().name() : null);
                res.setShipperLicensePlate(reg.getLicensePlate());
            });
        }
        return res;
    }

    /**
     * Enrich cả TRANG theo lô: review + hồ sơ shipper của tất cả đơn được lấy bằng
     * 2 câu IN(...) thay vì mỗi đơn vài query → khử N+1. Voucher/quan hệ khác đọc từ
     * entity đã nạp (batch-fetch toàn cục lo phần lazy còn lại).
     */
    private List<OrderResponse> enrichPage(List<Order> orders) {
        List<OrderResponse> res = orders.stream().map(orderMapper::toResponse).collect(Collectors.toList());
        if (orders.isEmpty()) return res;

        List<Long> orderIds = orders.stream().map(Order::getOrderId).toList();
        Map<Long, Review> reviewByOrder = reviewRepository.findByOrderOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(r -> r.getOrder().getOrderId(), r -> r, (a, b) -> a));

        List<Long> shipperUserIds = orders.stream()
                .map(o -> o.getShipper() != null ? o.getShipper().getUserId() : null)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, ShipperRegister> regByUser = shipperUserIds.isEmpty()
                ? Map.of()
                : shipperRegisterRepository.findByUserUserIdIn(shipperUserIds).stream()
                    .collect(Collectors.toMap(reg -> reg.getUser().getUserId(), reg -> reg,
                            (a, b) -> a.getRegisterId() >= b.getRegisterId() ? a : b));

        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            OrderResponse r = res.get(i);

            if (o.getUserVoucher() != null && o.getUserVoucher().getVoucher() != null) {
                r.setVoucherCode(o.getUserVoucher().getVoucher().getCode());
            }

            Review rv = reviewByOrder.get(o.getOrderId());
            if (rv != null) {
                r.setReviewed(true);
                r.setRestaurantRating(rv.getRestaurantRating());
                r.setShipperRating(rv.getShipperRating());
            } else {
                r.setReviewed(false);
            }

            if (r.getShipperId() != null) {
                ShipperRegister reg = regByUser.get(r.getShipperId());
                if (reg != null) {
                    r.setShipperVehicleType(reg.getVehicleType() != null ? reg.getVehicleType().name() : null);
                    r.setShipperLicensePlate(reg.getLicensePlate());
                }
            }
        }
        return res;
    }
}
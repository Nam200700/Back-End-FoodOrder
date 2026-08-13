package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.DTO.request.grouporder.*;
import org.example.datn.DTO.response.grouporder.GroupOrderMemberResponse;
import org.example.datn.DTO.response.grouporder.GroupOrderResponse;
import org.example.datn.DTO.response.order.OrderResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.*;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.*;
import org.example.datn.mapper.GroupOrderMapper;
import org.example.datn.mapper.GroupOrderMemberMapper;
import org.example.datn.mapper.OrderMapper;
import org.example.datn.util.ShippingFeeCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupOrderService {
    private final GroupOrderRepository groupOrderRepository;
    private final GroupOrderMemberRepository memberRepository;
    private final GroupOrderItemRepository groupItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final ShippingService shippingService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final WebSocketService webSocketService;
    private final GroupOrderMapper groupOrderMapper;
    private final GroupOrderMemberMapper groupOrderMemberMapper;
    private final OrderMapper orderMapper;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    // ─── Tạo phiên ─────────────────────────────────────────────
    @Transactional
    public GroupOrderResponse createGroupOrder(Long hostUserId, CreateGroupOrderRequest req) {
        User host = userRepository.findByIdOrThrow(hostUserId, ErrorCode.USER_NOT_FOUND);
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(req.getRestaurantId(), ErrorCode.RESTAURANT_NOT_FOUND);

        if (!Boolean.TRUE.equals(restaurant.getStatus())) {
            throw new AppException(ErrorCode.RESTAURANT_CLOSED, "Quán hiện không nhận đơn.");
        }

        CustomerAddress address = null;
        String deliveryAddress = req.getDeliveryAddress();
        BigDecimal lat = req.getDeliveryLat();
        BigDecimal lng = req.getDeliveryLng();

        if (req.getAddressId() != null) {
            address = customerAddressRepository.findById(req.getAddressId())
                    .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
            if (!address.getCustomer().getUserId().equals(hostUserId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Địa chỉ không thuộc về bạn");
            }
            deliveryAddress = address.getAddress();
            lat = address.getLatitude();
            lng = address.getLongitude();
        }

        if (deliveryAddress == null || lat == null || lng == null) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND, "Vui lòng chọn địa chỉ giao hàng hợp lệ.");
        }

        GroupOrder groupOrder = GroupOrder.builder()
                .host(host)
                .restaurant(restaurant)
                .inviteCode(generateUniqueInviteCode())
                .status(GroupOrderStatus.OPEN)
                .address(address)
                .deliveryAddress(deliveryAddress)
                .deliveryLat(lat)
                .deliveryLng(lng)
                .joinDeadline(req.getJoinDeadline())
                .note(req.getNote())
                .build();

        GroupOrderMember hostMember = GroupOrderMember.builder()
                .groupOrder(groupOrder)
                .user(host)
                .isHost(true)
                .status(GroupOrderMemberStatus.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();

        groupOrder.getMembers().add(hostMember);
        GroupOrder saved = groupOrderRepository.save(groupOrder);

        return toResponseWithInvite(saved);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = UUID.randomUUID().toString();
            if (!groupOrderRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể tạo mã mời, vui lòng thử lại.");
    }

    // ─── Tham gia / rời phiên ──────────────────────────────────
    @Transactional
    public GroupOrderResponse joinGroupOrder(Long userId, String inviteCode) {
        GroupOrder groupOrder = groupOrderRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_NOT_FOUND));

        ensureJoinable(groupOrder);

        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);

        try {
            memberRepository.findByGroupOrderGroupOrderIdAndUserUserId(groupOrder.getGroupOrderId(), userId)
                    .ifPresentOrElse(existing -> {
                        if (existing.getStatus() == GroupOrderMemberStatus.LEFT) {
                            existing.setStatus(GroupOrderMemberStatus.JOINED);
                            existing.setLeftAt(null);
                            memberRepository.save(existing);
                        }
                    }, () -> {
                        GroupOrderMember member = GroupOrderMember.builder()
                                .groupOrder(groupOrder)
                                .user(user)
                                .isHost(false)
                                .status(GroupOrderMemberStatus.JOINED)
                                .joinedAt(LocalDateTime.now())
                                .build();
                        memberRepository.saveAndFlush(member);
                    });
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Join race detected: groupOrderId={}, userId={} — coi như đã join thành công.",
                    groupOrder.getGroupOrderId(), userId);
        }

        return toResponseWithInvite(getOrThrowDetail(groupOrder.getGroupOrderId()));
    }

    @Transactional
    public void leaveGroupOrder(Long userId, Long groupOrderId) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureJoinable(groupOrder);

        GroupOrderMember member = memberRepository
                .findByGroupOrderGroupOrderIdAndUserUserId(groupOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_MEMBER_NOT_FOUND));

        if (Boolean.TRUE.equals(member.getIsHost())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Host không thể rời phiên, hãy hủy phiên thay vào đó.");
        }

        groupItemRepository.deleteByMemberMemberId(member.getMemberId());
        member.setStatus(GroupOrderMemberStatus.LEFT);
        member.setLeftAt(LocalDateTime.now());
        memberRepository.save(member);
    }

    // ─── Món ăn ─────────────────────────────────────────────────
    @Transactional
    public GroupOrderResponse addItem(Long userId, Long groupOrderId, AddGroupOrderItemRequest req) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureJoinable(groupOrder);

        GroupOrderMember member = memberRepository
                .findByGroupOrderGroupOrderIdAndUserUserId(groupOrderId, userId)
                .filter(m -> m.getStatus() != GroupOrderMemberStatus.LEFT)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_MEMBER_NOT_FOUND, "Bạn chưa tham gia phiên này"));

        Food food = foodRepository.findByIdOrThrow(req.getFoodId(), ErrorCode.FOOD_NOT_FOUND);
        if (!food.getRestaurant().getRestaurantId().equals(groupOrder.getRestaurant().getRestaurantId())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Món ăn không thuộc quán của phiên này");
        }
        if (!Boolean.TRUE.equals(food.getStatus()) || !Boolean.TRUE.equals(food.getIsAvailable())) {
            throw new AppException(ErrorCode.FOOD_NOT_FOUND, "Món " + food.getFoodName() + " hiện không thể đặt");
        }

        GroupOrderItem item = GroupOrderItem.builder()
                .groupOrder(groupOrder)
                .member(member)
                .food(food)
                .quantity(req.getQuantity())
                .priceAtAdd(food.getPrice())
                .note(req.getNote())
                .build();
        groupItemRepository.save(item);

        if (member.getStatus() == GroupOrderMemberStatus.READY) {
            member.setStatus(GroupOrderMemberStatus.JOINED);
            memberRepository.save(member);
        }

        return toResponseWithInvite(getOrThrowDetail(groupOrderId));
    }

    @Transactional
    public GroupOrderResponse updateItem(Long userId, Long groupOrderId, Long itemId, UpdateGroupOrderItemRequest req) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureJoinable(groupOrder);

        GroupOrderMember member = memberRepository
                .findByGroupOrderGroupOrderIdAndUserUserId(groupOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_MEMBER_NOT_FOUND));

        GroupOrderItem item = groupItemRepository.findByGroupOrderItemIdAndMemberMemberId(itemId, member.getMemberId())
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_ITEM_NOT_FOUND));

        item.setQuantity(req.getQuantity());
        item.setNote(req.getNote());
        groupItemRepository.save(item);

        return toResponseWithInvite(getOrThrowDetail(groupOrderId));
    }

    @Transactional
    public GroupOrderResponse removeItem(Long userId, Long groupOrderId, Long itemId) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureJoinable(groupOrder);

        GroupOrderMember member = memberRepository
                .findByGroupOrderGroupOrderIdAndUserUserId(groupOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_MEMBER_NOT_FOUND));

        GroupOrderItem item = groupItemRepository.findByGroupOrderItemIdAndMemberMemberId(itemId, member.getMemberId())
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_ITEM_NOT_FOUND));

        groupItemRepository.delete(item);
        groupItemRepository.flush();
        return toResponseWithInvite(getOrThrowDetail(groupOrderId));
    }

    // ─── Trạng thái thành viên / phiên ────────────────────────
    @Transactional
    public GroupOrderResponse markReady(Long userId, Long groupOrderId) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureJoinable(groupOrder);

        GroupOrderMember member = memberRepository
                .findByGroupOrderGroupOrderIdAndUserUserId(groupOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_MEMBER_NOT_FOUND));

        List<GroupOrderItem> items = groupItemRepository.findByMemberMemberId(member.getMemberId());
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Bạn chưa chọn món nào");
        }

        member.setStatus(GroupOrderMemberStatus.READY);
        memberRepository.save(member);
        return toResponseWithInvite(getOrThrowDetail(groupOrderId));
    }

    @Transactional
    public GroupOrderResponse lockGroupOrder(Long hostUserId, Long groupOrderId) {
        GroupOrder groupOrder = getOrThrowDetail(groupOrderId);
        ensureHost(groupOrder, hostUserId);
        ensureJoinable(groupOrder);

        if (groupOrder.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Chưa có món nào được chọn trong phiên");
        }

        groupOrder.setStatus(GroupOrderStatus.LOCKED);
        groupOrder.setLockedAt(LocalDateTime.now());

        // [MỚI] Khóa phiên = không ai còn thêm/sửa món được nữa (nút "Hoàn tất chọn món"
        // chỉ hiện khi status = OPEN) → tự động chuyển các thành viên còn "JOINED" sang
        // "READY", tránh kẹt vĩnh viễn ở trạng thái "Đang chọn" dù không còn cách thao tác.
        List<GroupOrderMember> toMarkReady = groupOrder.getMembers().stream()
                .filter(m -> m.getStatus() == GroupOrderMemberStatus.JOINED)
                .collect(Collectors.toList());
        toMarkReady.forEach(m -> m.setStatus(GroupOrderMemberStatus.READY));
        if (!toMarkReady.isEmpty()) {
            memberRepository.saveAll(toMarkReady);
        }

        groupOrderRepository.save(groupOrder);

        return toResponseWithInvite(groupOrder);
    }

    @Transactional
    public void cancelGroupOrder(Long hostUserId, Long groupOrderId, String reason) {
        GroupOrder groupOrder = getOrThrow(groupOrderId);
        ensureHost(groupOrder, hostUserId);

        if (groupOrder.getStatus() == GroupOrderStatus.ORDERED) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiên đã chốt thành đơn hàng, không thể hủy tại đây");
        }
        if (groupOrder.getStatus() == GroupOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiên đã bị hủy trước đó");
        }

        groupOrder.setStatus(GroupOrderStatus.CANCELLED);
        groupOrder.setNote(reason);
        groupOrderRepository.save(groupOrder);
    }

    // ─── Chốt đơn:  ───────
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OrderResponse checkout(Long hostUserId, Long groupOrderId, CheckoutGroupOrderRequest req) {
        GroupOrder groupOrder = getOrThrowDetail(groupOrderId);
        ensureHost(groupOrder, hostUserId);

        if (groupOrder.getStatus() != GroupOrderStatus.LOCKED && groupOrder.getStatus() != GroupOrderStatus.OPEN) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiên không ở trạng thái có thể chốt đơn");
        }
        if (groupOrder.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Chưa có món nào để đặt");
        }

        // [MỚI] Chặn chốt đơn nếu còn thành viên (trừ host) chưa "Hoàn tất chọn món",
        // trừ khi host xác nhận bỏ qua (force=true) — tránh chốt hụt món của người
        // đang chọn dở, đồng thời không khóa cứng nếu có người "mất tích" không phản hồi.
        ensureMembersReadyOrForce(groupOrder, req.isForce());

        Restaurant restaurant = groupOrder.getRestaurant();
        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND, "Quán chưa cập nhật tọa độ vị trí.");
        }

        double distance = shippingService.getDistanceKm(
                restaurant.getLatitude().doubleValue(), restaurant.getLongitude().doubleValue(),
                groupOrder.getDeliveryLat().doubleValue(), groupOrder.getDeliveryLng().doubleValue()
        );
        if (distance > 10.0) {
            throw new AppException(ErrorCode.DISTANCE_TOO_FAR,
                    String.format("Quán cách địa chỉ giao %.1f km, vượt quá 10 km cho phép!", distance));
        }

        BigDecimal shippingFee = BigDecimal.valueOf(ShippingFeeCalculator.calculate(distance));

        UserVoucher userVoucher = null;
        Voucher voucher = null;
        if (req.getUserVoucherId() != null) {
            userVoucher = userVoucherRepository.findById(req.getUserVoucherId())
                    .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
            if (!userVoucher.getUser().getUserId().equals(hostUserId)) {
                throw new AppException(ErrorCode.VOUCHER_NOT_OWNED);
            }
            if (Boolean.TRUE.equals(userVoucher.getUsed())) {
                throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);
            }
            voucher = userVoucher.getVoucher();
        }

        User customer = userRepository.getReferenceById(hostUserId);

        Order order = Order.builder()
                .customer(customer)
                .restaurant(restaurant)
                .groupOrder(groupOrder)
                .deliveryAddress(groupOrder.getDeliveryAddress())
                .deliveryLat(groupOrder.getDeliveryLat())
                .deliveryLng(groupOrder.getDeliveryLng())
                .address(groupOrder.getAddress())
                .paymentMethod(req.getPaymentMethod())
                .discountAmount(BigDecimal.ZERO)
                .orderStatus(OrderStatus.PENDING)
                .note(req.getNote() != null ? req.getNote() : groupOrder.getNote())
                .userVoucher(userVoucher)
                .shippingFee(shippingFee)
                .build();

        List<OrderItem> orderItems = groupOrder.getItems().stream().map(gi -> OrderItem.builder()
                .order(order)
                .food(gi.getFood())
                .foodName(gi.getFood().getFoodName())
                .quantity(gi.getQuantity())
                .priceAtOrder(gi.getPriceAtAdd())
                .note(gi.getNote())
                .groupOrderMember(gi.getMember())
                .build()).collect(Collectors.toList());

        order.getItems().addAll(orderItems);

        BigDecimal subtotal = orderItems.stream()
                .map(i -> i.getPriceAtOrder().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotalAmount(subtotal);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (voucher != null) {
            if (voucher.getMinOrderAmount() != null && subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        String.format("Tiền món (%,d VNĐ) chưa đạt tối thiểu %,d VNĐ để áp mã '%s'!",
                                subtotal.longValue(), voucher.getMinOrderAmount().longValue(), voucher.getCode()));
            }
            switch (voucher.getDiscountType()) {
                case FIXED -> discountAmount = voucher.getDiscountValue();
                case PERCENT -> discountAmount = subtotal.multiply(voucher.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                case FREESHIP -> discountAmount = shippingFee;
            }
            userVoucher.setUsed(true);
            userVoucher.setUsedAt(LocalDateTime.now());
            userVoucherRepository.save(userVoucher);
            voucher.setUsedQuantity((voucher.getUsedQuantity() != null ? voucher.getUsedQuantity() : 0) + 1);
        }
        order.setDiscountAmount(discountAmount);

        BigDecimal total = subtotal.add(shippingFee).subtract(discountAmount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        paymentService.createForOrder(saved);

        groupOrder.setStatus(GroupOrderStatus.ORDERED);
        groupOrderRepository.save(groupOrder);

        try {
            if (restaurant.getOwner() != null) {
                notificationService.notifyUser(restaurant.getOwner().getUserId(),
                        NotificationType.ORDER_NEW, saved.getOrderId());
            }
            webSocketService.broadcastOrderStatus(saved);
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo/websocket cho đơn nhóm ID: {}", saved.getOrderId(), e);
        }

        return orderMapper.toResponse(saved);
    }

    /** Kiểm tra còn thành viên (trừ host) chưa READY không, trừ khi force=true. */
    private void ensureMembersReadyOrForce(GroupOrder groupOrder, boolean force) {
        if (force) return;

        long notReadyCount = groupOrder.getMembers().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsHost()))
                .filter(m -> m.getStatus() != GroupOrderMemberStatus.LEFT)
                .filter(m -> m.getStatus() != GroupOrderMemberStatus.READY)
                .count();

        if (notReadyCount > 0) {
            throw new AppException(ErrorCode.GROUP_ORDER_MEMBERS_NOT_READY,
                    notReadyCount + " thành viên chưa hoàn tất chọn món. Xác nhận nếu vẫn muốn chốt đơn.");
        }
    }

    // ─── Truy vấn ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public GroupOrderResponse getDetail(Long userId, Long groupOrderId) {
        GroupOrder groupOrder = getOrThrowDetail(groupOrderId);
        boolean isMember = groupOrder.getMembers().stream()
                .anyMatch(m -> m.getUser().getUserId().equals(userId));
        if (!isMember) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không phải thành viên của phiên này");
        }
        return toResponseWithInvite(groupOrder);
    }

    @Transactional(readOnly = true)
    public GroupOrderResponse getByInviteCode(String inviteCode) {
        GroupOrder groupOrder = groupOrderRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_NOT_FOUND));
        GroupOrder detail = getOrThrowDetail(groupOrder.getGroupOrderId());
        return toResponseWithInvite(detail);
    }

    @Transactional(readOnly = true)
    public Page<GroupOrderResponse> getMyGroupOrders(Long userId, Pageable pageable) {
        return groupOrderRepository.findByMemberUserId(userId, pageable)
                .map(this::toResponseWithInvite);
    }

    @Transactional(readOnly = true)
    public GroupOrderResponse getActiveGroupOrderForRestaurant(Long userId, Long restaurantId) {
        List<GroupOrder> actives = groupOrderRepository.findActiveByUserAndRestaurant(userId, restaurantId);
        if (actives.isEmpty()) return null;
        GroupOrder detail = getOrThrowDetail(actives.get(0).getGroupOrderId());
        return toResponseWithInvite(detail);
    }

    // ─── Helpers ─────────────────────────────────────────────
    private GroupOrder getOrThrow(Long groupOrderId) {
        return groupOrderRepository.findById(groupOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_NOT_FOUND));
    }

    private GroupOrder getOrThrowDetail(Long groupOrderId) {
        return groupOrderRepository.findDetailById(groupOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_ORDER_NOT_FOUND));
    }

    private void ensureHost(GroupOrder groupOrder, Long userId) {
        if (!groupOrder.getHost().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ chủ phiên mới có quyền thực hiện thao tác này");
        }
    }

    private void ensureJoinable(GroupOrder groupOrder) {
        if (groupOrder.getStatus() != GroupOrderStatus.OPEN) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiên đã khóa/hủy/hết hạn, không thể thao tác");
        }
        if (groupOrder.getJoinDeadline() != null && groupOrder.getJoinDeadline().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiên đã hết hạn tham gia");
        }
    }

    private String buildInviteUrl(GroupOrder g) {
        return String.format("%s/restaurants/%d?group=%s",
                frontendBaseUrl, g.getRestaurant().getRestaurantId(), g.getInviteCode());
    }

    private GroupOrderResponse toResponse(GroupOrder g) {
        GroupOrderResponse response = groupOrderMapper.toResponse(g);
        Map<Long, List<GroupOrderItem>> itemsByMember =
                g.getItems().stream().collect(Collectors.groupingBy(item -> item.getMember().getMemberId()));
        List<GroupOrderMemberResponse> members =
                g.getMembers().stream()
                        .sorted(Comparator.comparing(GroupOrderMember::getJoinedAt))
                        .map(member -> {
                            List<GroupOrderItem> items = itemsByMember.getOrDefault(member.getMemberId(), List.of());
                            return groupOrderMemberMapper.toResponse(member, items);
                        }).toList();
        response.setMembers(members);
        return response;
    }

    private GroupOrderResponse toResponseWithInvite(GroupOrder g) {
        GroupOrderResponse res = toResponse(g);
        res.setInviteUrl(buildInviteUrl(g));
        return res;
    }

    // ─── Job tự động hết hạn ────────────────────────────────────
    @Transactional
    public void expireOverdueGroupOrders() {
        List<GroupOrder> overdue = groupOrderRepository
                .findByStatusAndJoinDeadlineBefore(GroupOrderStatus.OPEN, LocalDateTime.now());
        for (GroupOrder g : overdue) {
            g.setStatus(GroupOrderStatus.EXPIRED);
        }
        if (!overdue.isEmpty()) {
            groupOrderRepository.saveAll(overdue);
        }
    }
}
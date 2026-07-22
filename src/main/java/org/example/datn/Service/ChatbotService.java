package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.*;
import org.example.datn.domain.*;
import org.example.datn.domain.enums.Sender;
import org.example.datn.DTO.response.chatbot.ChatMessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final FoodRepository foodRepository;
    private final FavoriteRestaurantRepository favoriteRestaurantRepository;
    private final OrderRepository orderRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @org.springframework.beans.factory.annotation.Value("${app.openrouter.api-key:}")
    private String openrouterApiKey;

    @Transactional
    public Long resolveGuestUserId() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == org.example.datn.domain.enums.Role.CUSTOMER)
                .map(User::getUserId)
                .findFirst()
                .orElseGet(() -> {
                    User mock = User.builder()
                            .fullName("Khách Trải Nghiệm")
                            .phone("0999999999")
                            .email("guest_chatbot@freshdelivery.vn")
                            .password("NoPasswordAllowedForGuests123!")
                            .role(org.example.datn.domain.enums.Role.CUSTOMER)
                            .build();
                    return userRepository.save(mock).getUserId();
                });
    }

    @Transactional
    public String chatWithAI(Long userId, String userMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 1. Chống Spam: Giới hạn 5 tin nhắn mỗi 1 phút
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long messageCount = chatMessageRepository.countByUserUserIdAndSenderAndCreatedAtAfter(userId, Sender.USER, oneMinuteAgo);
        if (messageCount >= 5) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Bạn đã gửi quá nhiều tin nhắn. Vui lòng thử lại sau 1 phút!");
        }

        // 2. Lấy danh sách 5 tin nhắn gần nhất trong lịch sử chat để tạo bộ nhớ hội thoại
        List<ChatMessage> history = chatMessageRepository.findTop5ByUserUserIdOrderByCreatedAtDesc(userId);
        Collections.reverse(history);

        // 3. Lấy Ngữ cảnh Quán ăn & Món ăn
        String restaurantContext = buildRestaurantContext();

        // 4. Lấy Ngữ cảnh Sở thích khách hàng (Quán yêu thích + Lịch sử đơn hàng để gợi ý món)
        String userPreferenceContext = buildUserPreferenceContext(userId);

        // 5. Chuẩn bị danh sách tin nhắn theo định dạng OpenAI chat completions cho OpenRouter
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        String systemPrompt = buildSystemPrompt(restaurantContext, userPreferenceContext);
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        // History messages
        for (ChatMessage msg : history) {
            Map<String, String> histMsg = new HashMap<>();
            histMsg.put("role", msg.getSender() == Sender.USER ? "user" : "assistant");
            histMsg.put("content", msg.getContent());
            messages.add(histMsg);
        }

        // Latest user message
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // 6. Gọi API OpenRouter
        String botResponse = callOpenRouterAPI(messages);

        // 7. Lưu tin nhắn User & Bot vào Database
        ChatMessage userMsgEntity = ChatMessage.builder()
                .user(user)
                .sender(Sender.USER)
                .content(userMessage)
                .build();
        chatMessageRepository.save(userMsgEntity);

        ChatMessage botMsgEntity = ChatMessage.builder()
                .user(user)
                .sender(Sender.BOT)
                .content(botResponse)
                .build();
        chatMessageRepository.save(botMsgEntity);

        return botResponse;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long userId) {
        List<ChatMessage> list = chatMessageRepository.findTop50ByUserUserIdOrderByCreatedAtDesc(userId);
        Collections.reverse(list);
        return list.stream()
                .map(msg -> ChatMessageResponse.builder()
                        .sender(msg.getSender())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private String buildRestaurantContext() {
        List<Restaurant> restaurants = restaurantRepository.findByStatusTrue(Pageable.unpaged()).getContent();
        StringBuilder sb = new StringBuilder();
        for (Restaurant r : restaurants) {
            sb.append("- Quán ăn: ").append(r.getRestaurantName())
                    .append(" (ID: ").append(r.getRestaurantId()).append(")\n")
                    .append("  Mô tả: ").append(r.getDescription() != null ? r.getDescription() : "Chưa có mô tả").append("\n")
                    .append("  Địa chỉ: ").append(r.getAddress() != null ? r.getAddress() : "Chưa có địa chỉ").append("\n");

            List<Food> foods = foodRepository.findActiveByRestaurantId(r.getRestaurantId());
            if (!foods.isEmpty()) {
                sb.append("  Danh sách thực đơn món ăn:\n");
                for (Food f : foods) {
                    sb.append("    + ").append(f.getFoodName())
                            .append(" (Giá: ").append(f.getPrice()).append(" VND)\n")
                            .append("      Mô tả món: ").append(f.getDescription() != null ? f.getDescription() : "Ngon, bổ, rẻ").append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildUserPreferenceContext(Long userId) {
        StringBuilder sb = new StringBuilder();

        // 1. Quán ăn yêu thích
        List<FavoriteRestaurant> favorites = favoriteRestaurantRepository.findByCustomerUserId(userId);
        if (!favorites.isEmpty()) {
            sb.append("- Quán ăn yêu thích của khách hàng:\n");
            for (FavoriteRestaurant fav : favorites) {
                sb.append("  + ").append(fav.getRestaurant().getRestaurantName()).append("\n");
            }
        } else {
            sb.append("- Khách hàng chưa lưu quán ăn yêu thích nào.\n");
        }

        // 2. Lịch sử đặt hàng gần đây
        Page<Order> orders = orderRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5));
        if (orders.hasContent()) {
            sb.append("- Lịch sử 5 đơn hàng gần đây nhất:\n");
            for (Order o : orders.getContent()) {
                sb.append("  + Đơn hàng ngày ").append(o.getCreatedAt())
                        .append(" tại ").append(o.getRestaurant().getRestaurantName())
                        .append(" (Tổng: ").append(o.getTotalAmount()).append(" VND, Trạng thái: ").append(o.getOrderStatus()).append(")\n")
                        .append("    Các món đã gọi:\n");
                if (o.getItems() != null) {
                    for (OrderItem item : o.getItems()) {
                        sb.append("      * ").append(item.getFood().getFoodName())
                                .append(" x").append(item.getQuantity()).append("\n");
                    }
                }
            }
        } else {
            sb.append("- Khách hàng chưa từng đặt đơn hàng nào trên hệ thống.\n");
        }

        return sb.toString();
    }

    private String buildSystemPrompt(String restaurantContext, String preferenceContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là Trợ lý ảo AI cực kỳ thông minh của hệ thống đặt đồ ăn trực tuyến Fresh Delivery.\n");
        sb.append("Nhiệm vụ của bạn là tư vấn cho khách hàng tìm được món ăn ưng ý nhất, hướng dẫn đặt món và trả lời các câu hỏi về thực phẩm, quán ăn liên kết với Fresh Delivery.\n\n");

        sb.append("=== QUY TẮC PHỤC VỤ & BẢO MẬT ===\n");
        sb.append("1. TRẢ LỜI LỊCH SỰ, CHUYÊN NGHIỆP: Hãy trả lời khách hàng một cách thân thiện, lễ phép và chuyên nghiệp.\n");
        sb.append("2. PHÙ HỢP THUẦN PHONG MỸ TỤC, CHẶN 18+: Tuyệt đối KHÔNG trả lời hay tạo ra nội dung thô tục, khiêu dâm, bạo lực hay vi phạm đạo đức. Nếu khách hàng nói lời thô tục hoặc nhạy cảm, hãy trả lời một cách lịch sự nhưng cứng rắn từ chối trả lời chủ đề đó và nhắc nhở họ nhắn tin lịch sự.\n");
        sb.append("3. AN TOÀN & BẢO MẬT HỆ THỐNG: Nếu người dùng hỏi các câu hỏi liên quan đến mã nguồn backend, cấu trúc database, lỗ hổng bảo mật hệ thống, tài khoản quản trị viên hoặc các vấn đề an ninh thông tin của dự án, tuyệt đối KHÔNG trả lời. Hãy trả lời: 'Xin lỗi, tôi là trợ lý ảo hỗ trợ đặt món và không được phép cung cấp thông tin kỹ thuật của hệ thống.'\n");
        sb.append("4. CÁ NHÂN HÓA GỢI Ý MÓN ĂN: Dựa vào sở thích và món ăn đã đặt gần đây của người dùng, hãy chủ động ưu thiện gợi ý các món ăn phù hợp với khẩu vị của họ. Nếu họ chưa có lịch sử, hãy gợi ý món ăn ngẫu nhiên bán chạy trên hệ thống.\n");
        sb.append("5. BẢO MẬT THÔNG TIN CÁ NHÂN (CỰC KỲ QUAN TRỌNG): Tuyệt đối KHÔNG cung cấp, tiết lộ hay tìm kiếm bất kỳ thông tin cá nhân nào như họ tên thật, số điện thoại, email, mật khẩu hay địa chỉ của bất kỳ người dùng, khách hàng, chủ quán (Owner) hay tài xế (Shipper) nào khác trên hệ thống. Nếu người dùng hỏi các thông tin cá nhân hoặc yêu cầu cung cấp danh sách người dùng/số điện thoại, hãy lịch sự từ chối trả lời: 'Để đảm bảo an toàn thông tin và tuân thủ chính sách bảo mật, tôi không được phép cung cấp thông tin cá nhân của người dùng trên hệ thống Fresh Delivery.'\n\n");

        sb.append("=== THỰC ĐƠN VÀ QUÁN ĂN CÓ SẴN TRÊN HỆ THỐNG ===\n");
        sb.append(restaurantContext).append("\n");

        sb.append("=== LỊCH SỬ VÀ SỞ THÍCH CỦA NGƯỜI DÙNG ===\n");
        sb.append(preferenceContext).append("\n");

        return sb.toString();
    }

    private String callOpenRouterAPI(List<Map<String, String>> messages) {
        String key = openrouterApiKey;
        if (key == null || key.trim().isEmpty()) {
            String encodedKey = "c2stb3ItdjEtZGVkNzhhZjdiYTdhNzNlYjc2NzAwNzJkN2I5YjUwZjI2MmJmMmYwYWIyNzRkNDkyZjBkMzdiMzMzMDM4ZDA0NQ==";
            key = new String(java.util.Base64.getDecoder().decode(encodedKey));
        }
        String model = "tencent/hy3";
        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + key);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "Fresh Delivery Chatbot");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List choices = (List) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map choice = (Map) choices.get(0);
                    Map message = (Map) choice.get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi API OpenRouter: ", e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể kết nối đến máy chủ AI lúc này!");
        }

        throw new AppException(ErrorCode.INTERNAL_ERROR, "Dữ liệu phản hồi từ AI không đúng định dạng!");
    }
}

-- ============================================================
--  AntiGravity Food — Full Schema (V2)
--  Bao gồm: V1 gốc + tất cả migration cải tiến
--  Thứ tự: CREATE TABLE → UNIQUE → INDEX → FK → MIGRATION
-- ============================================================

-- ─── V1: Core tables ─────────────────────────────────────────

CREATE TABLE users (
                       status          BIT NOT NULL,
                       created_at      DATETIME(6),
                       updated_at      DATETIME(6),
                       deleted_at      DATETIME(6)                 NULL,           -- [V2] soft delete
                       deleted_by      BIGINT                      NULL,           -- [V2] ai xóa
                       locked_at       DATETIME(6)                 NULL,           -- [V2] lúc bị khóa
                       locked_reason   VARCHAR(255)                NULL,           -- [V2] lý do khóa, phân biệt với owner và shipper chờ duyệt
                       user_id         BIGINT NOT NULL AUTO_INCREMENT,
                       phone           VARCHAR(15),
                       email           VARCHAR(100),
                       full_name       VARCHAR(100) NOT NULL,
                       google_id       VARCHAR(100),
                       avatar          VARCHAR(255),
                       password        VARCHAR(255),
                       role            ENUM('ADMIN','CUSTOMER','OWNER','SHIPPER') NOT NULL,
                       PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- [V2] Nhiều địa chỉ giao hàng cho mỗi customer
CREATE TABLE customer_addresses (
                                    address_id      BIGINT NOT NULL AUTO_INCREMENT,
                                    customer_id     BIGINT NOT NULL,
                                    label           VARCHAR(50)                 NULL,           -- "Nhà", "Văn phòng"...
                                    address         VARCHAR(255) NOT NULL,
                                    latitude        DECIMAL(10,7)               NULL,
                                    longitude       DECIMAL(10,7)               NULL,
                                    is_default      BIT NOT NULL DEFAULT 0,
                                    created_at      DATETIME(6),
                                    updated_at      DATETIME(6),
                                    PRIMARY KEY (address_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurants (
                             latitude        DECIMAL(10,7),
                             longitude       DECIMAL(10,7),
                             status          BIT NOT NULL,
                             status_detail   ENUM('ACTIVE','SELF_CLOSED','ADMIN_LOCKED','PENDING')
                    NOT NULL DEFAULT 'ACTIVE',                  -- [V2] lý do trạng thái
                             status_reason   VARCHAR(255)                NULL,           -- [V2] ghi chú khi lock
                             opens_at        TIME                        NULL,           -- [V2] giờ mở cửa
                             closes_at       TIME                        NULL,           -- [V2] giờ đóng cửa
                             created_at      DATETIME(6),
                             owner_id        BIGINT NOT NULL,
                             restaurant_id   BIGINT NOT NULL AUTO_INCREMENT,
                             updated_at      DATETIME(6),
                             phone           VARCHAR(15),
                             restaurant_name VARCHAR(150) NOT NULL,
                             description     VARCHAR(500),
                             address         VARCHAR(255),
                             image_url       VARCHAR(255),
                             PRIMARY KEY (restaurant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
                            display_order   INTEGER NOT NULL,
                            category_id     BIGINT NOT NULL AUTO_INCREMENT,
                            created_at      DATETIME(6),
                            restaurant_id   BIGINT NOT NULL,
                            updated_at      DATETIME(6),
                            category_name   VARCHAR(100) NOT NULL,
                            PRIMARY KEY (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE foods (
                       price           DECIMAL(12,2) NOT NULL,
                       status          BIT NOT NULL,
                       is_available    BIT NOT NULL DEFAULT 1,                     -- [V2] hết nguyên liệu tạm thời
                       category_id     BIGINT,
                       created_at      DATETIME(6),
                       food_id         BIGINT NOT NULL AUTO_INCREMENT,
                       restaurant_id   BIGINT NOT NULL,
                       updated_at      DATETIME(6),
                       food_name       VARCHAR(150) NOT NULL,
                       description     VARCHAR(500),
                       image_url       VARCHAR(255),
                       PRIMARY KEY (food_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE carts (
                       cart_id         BIGINT NOT NULL AUTO_INCREMENT,
                       created_at      DATETIME(6),
                       customer_id     BIGINT NOT NULL,
                       restaurant_id   BIGINT NOT NULL,
                       updated_at      DATETIME(6),
                       PRIMARY KEY (cart_id)
    -- [V2] KHÔNG còn unique(customer_id) — cho phép nhiều cart per customer
    -- unique(customer_id, restaurant_id) được thêm bên dưới
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
                            quantity        INTEGER NOT NULL,
                            price_at_add    DECIMAL(12,2)   NULL,                       -- [V2] giá lúc thêm vào giỏ, tránh lệch giá khi checkout
                            cart_id         BIGINT NOT NULL,
                            cart_item_id    BIGINT NOT NULL AUTO_INCREMENT,
                            created_at      DATETIME(6),
                            food_id         BIGINT NOT NULL,
                            updated_at      DATETIME(6),
                            note            VARCHAR(255),
                            PRIMARY KEY (cart_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
                        delivery_lat        DECIMAL(10,7),
                        delivery_lng        DECIMAL(10,7),
                        discount_amount     DECIMAL(10,2) NOT NULL,
                        shipping_fee        DECIMAL(10,2) NOT NULL,
                        subtotal_amount     DECIMAL(12,2) NOT NULL,
                        total_amount        DECIMAL(12,2) NOT NULL,
                        completed_at        DATETIME(6),
                        confirmed_at        DATETIME(6),
                        preparing_at        DATETIME(6)             NULL,           -- [V2] lúc bắt đầu nấu
                        ready_at            DATETIME(6)             NULL,           -- [V2] lúc món xong, chờ shipper
                        picked_up_at        DATETIME(6)             NULL,           -- [V2] lúc shipper lấy hàng
                        created_at          DATETIME(6),
                        customer_id         BIGINT NOT NULL,
                        order_id            BIGINT NOT NULL AUTO_INCREMENT,
                        restaurant_id       BIGINT NOT NULL,
                        shipper_id          BIGINT,
                        cancelled_by        BIGINT                  NULL,           -- [V2] ai hủy đơn
                        address_id          BIGINT                  NULL,           -- [V2] địa chỉ từ customer_addresses, NULL nếu nhập mới
                        updated_at          DATETIME(6),
                        cancel_reason       VARCHAR(300),
                        delivery_address    VARCHAR(255) NOT NULL,
                        note                VARCHAR(255),
                        order_status        ENUM('CANCELLED','COMPLETED','CONFIRMED','DELIVERING',
                             'PENDING','PICKED_UP','PREPARING','READY_FOR_PICKUP') NOT NULL,
                        payment_method      ENUM('COD') NOT NULL,
                        payment_status      ENUM('FAILED','PAID','PENDING','REFUNDED') NOT NULL,
                        PRIMARY KEY (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
                             price_at_order  DECIMAL(12,2) NOT NULL,
                             quantity        INTEGER NOT NULL,
                             food_id         BIGINT,
                             order_id        BIGINT NOT NULL,
                             order_item_id   BIGINT NOT NULL AUTO_INCREMENT,
                             food_name       VARCHAR(150) NOT NULL,
                             note            VARCHAR(255),
                             PRIMARY KEY (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE deliveries (
                            assigned_at     DATETIME(6),
                            completed_at    DATETIME(6),
                            created_at      DATETIME(6),
                            delivery_id     BIGINT NOT NULL AUTO_INCREMENT,
                            order_id        BIGINT NOT NULL,
                            shipper_id      BIGINT NOT NULL,
                            updated_at      DATETIME(6),
                            PRIMARY KEY (delivery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
                          amount          DECIMAL(12,2) NOT NULL,
                          created_at      DATETIME(6),
                          order_id        BIGINT NOT NULL,
                          paid_at         DATETIME(6),
                          payment_id      BIGINT NOT NULL AUTO_INCREMENT,
                          updated_at      DATETIME(6),
                          confirmed_by    BIGINT          NULL,                       -- [V2] shipper xác nhận đã thu tiền COD
                          transaction_no  VARCHAR(100),
                          method          ENUM('COD') NOT NULL,
                          status          ENUM('FAILED','PAID','PENDING','REFUNDED') NOT NULL,
                          PRIMARY KEY (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transactions (
                              amount          DECIMAL(12,2) NOT NULL,
                              created_at      DATETIME(6),
                              order_id        BIGINT NOT NULL,
                              payment_id      BIGINT          NULL,                       -- [V2] liên kết payments để đối soát
                              transaction_id  BIGINT NOT NULL AUTO_INCREMENT,
                              updated_at      DATETIME(6),
                              user_id         BIGINT NOT NULL,
                              type            ENUM('MERCHANT_EARNING','REFUND','SHIPPER_EARNING') NOT NULL,
                              PRIMARY KEY (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE reviews (
                         restaurant_rating   INTEGER NOT NULL,
                         shipper_rating      INTEGER,
                         created_at          DATETIME(6),
                         customer_id         BIGINT NOT NULL,
                         order_id            BIGINT NOT NULL,
                         replied_at          DATETIME(6),
                         restaurant_id       BIGINT NOT NULL,
                         shipper_id          BIGINT          NULL,                   -- [V2] tổng hợp rating shipper nhanh hơn
                         review_id           BIGINT NOT NULL AUTO_INCREMENT,
                         updated_at          DATETIME(6),
                         merchant_reply      VARCHAR(500),
                         restaurant_comment  VARCHAR(500),
                         shipper_comment     VARCHAR(500),
                         PRIMARY KEY (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [V2] Ảnh đính kèm review
CREATE TABLE review_images (
                               image_id        BIGINT NOT NULL AUTO_INCREMENT,
                               review_id       BIGINT NOT NULL,
                               image_url       VARCHAR(255) NOT NULL,
                               display_order   INTEGER NOT NULL DEFAULT 0,
                               created_at      DATETIME(6),
                               PRIMARY KEY (image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE favorite_restaurants (
                                      created_at      DATETIME(6),
                                      customer_id     BIGINT NOT NULL,
                                      favorite_id     BIGINT NOT NULL AUTO_INCREMENT,
                                      restaurant_id   BIGINT NOT NULL,
                                      updated_at      DATETIME(6),
                                      PRIMARY KEY (favorite_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
                               is_read             BIT NOT NULL,
                               created_at          DATETIME(6),
                               notification_id     BIGINT NOT NULL AUTO_INCREMENT,
                               ref_id              BIGINT,
                               updated_at          DATETIME(6),
                               user_id             BIGINT NOT NULL,
                               title               VARCHAR(150) NOT NULL,
                               body                VARCHAR(500),
                               action_url          VARCHAR(255)            NULL,           -- [V2] deep link điều hướng
                               type                ENUM('ORDER_CANCELLED','ORDER_COMPLETED','ORDER_CONFIRMED',
                             'ORDER_NEW','ORDER_PREPARING','ORDER_READY_PICKUP',
                             'SHIPPER_ASSIGNED') NOT NULL,
    -- [V2] recipient_role: xác định vai trò nhận thông báo
    --   ORDER_NEW          → OWNER
    --   ORDER_CONFIRMED    → CUSTOMER
    --   ORDER_PREPARING    → CUSTOMER
    --   ORDER_READY_PICKUP → SHIPPER
    --   SHIPPER_ASSIGNED   → CUSTOMER
    --   ORDER_CANCELLED    → CUSTOMER | OWNER | SHIPPER (mỗi người 1 row riêng)
    --   ORDER_COMPLETED    → CUSTOMER | OWNER           (mỗi người 1 row riêng)
                               recipient_role      ENUM('ADMIN','CUSTOMER','OWNER','SHIPPER') NOT NULL,
                               PRIMARY KEY (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversations (
                               conversation_id     BIGINT NOT NULL AUTO_INCREMENT,
                               created_at          DATETIME(6),
                               last_message_at     DATETIME(6),
                               updated_at          DATETIME(6),
                               user1_id            BIGINT NOT NULL,
                               user2_id            BIGINT NOT NULL,
                               unread_count_user1  INT NOT NULL DEFAULT 0, -- [V2] tránh COUNT(*) mỗi lần
                               unread_count_user2  INT NOT NULL DEFAULT 0, -- [V2]
                               PRIMARY KEY (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
                          is_read         BIT NOT NULL,
                          conversation_id BIGINT NOT NULL,
                          created_at      DATETIME(6),
                          message_id      BIGINT NOT NULL AUTO_INCREMENT,
                          sender_id       BIGINT NOT NULL,
                          updated_at      DATETIME(6),
                          content         VARCHAR(1000) NOT NULL,
                          PRIMARY KEY (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE otps (
                      fail_count      INTEGER NOT NULL,
                      is_used         BIT NOT NULL,
                      created_at      DATETIME(6),
                      expired_at      DATETIME(6) NOT NULL,
                      otp_id          BIGINT NOT NULL AUTO_INCREMENT,
                      updated_at      DATETIME(6),
                      code            VARCHAR(10) NOT NULL,
                      phone           VARCHAR(15) NOT NULL,
                      purpose         ENUM('LOGIN','REGISTER','RESET_PASSWORD') NOT NULL,
                      PRIMARY KEY (otp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reports (
                         created_at      DATETIME(6),
                         report_id       BIGINT NOT NULL AUTO_INCREMENT,
                         reporter_id     BIGINT NOT NULL,
                         resolved_at     DATETIME(6),
                         target_id       BIGINT NOT NULL,
                         updated_at      DATETIME(6),
                         reason          VARCHAR(500) NOT NULL,
                         status          ENUM('PENDING','REJECTED','RESOLVED') NOT NULL,
                         target_type     ENUM('ORDER','RESTAURANT','REVIEW','USER') NOT NULL,
                         PRIMARY KEY (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE restaurant_registers (
                                      register_id     BIGINT NOT NULL AUTO_INCREMENT,
                                      owner_id        BIGINT NOT NULL,
                                      restaurant_name VARCHAR(100) NOT NULL,
                                      address         VARCHAR(255) NOT NULL,
                                      latitude        DECIMAL(10,7)   NULL,
                                      longitude       DECIMAL(10,7)   NULL,
                                      phone           VARCHAR(15) NOT NULL,
                                      image_url       VARCHAR(255),
                                      description     VARCHAR(500),
                                      status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
                                      rejected_reason VARCHAR(500),
                                      reviewed_at     DATETIME(6)     NULL,
                                      created_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                                      PRIMARY KEY (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shipper_registers (
                                   register_id     BIGINT NOT NULL AUTO_INCREMENT,
                                   user_id         BIGINT NOT NULL,
                                   id_card         VARCHAR(20) NOT NULL,
                                   vehicle_type    ENUM('MOTORBIKE','BICYCLE','CAR') NOT NULL,
                                   license_plate   VARCHAR(20) NOT NULL,
                                   status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
                                   rejected_reason VARCHAR(300),
                                   reviewed_at     DATETIME(6)     NULL,
                                   created_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                                   PRIMARY KEY (register_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Profile chính thức của shipper sau khi được duyệt.
-- Tách khỏi shipper_registers vì registers là đơn chờ duyệt,
-- còn bảng này là thông tin vận hành thực tế.
CREATE TABLE shippers (
                          shipper_id        BIGINT NOT NULL AUTO_INCREMENT,
                          user_id           BIGINT NOT NULL,
                          id_card           VARCHAR(20) NOT NULL,
                          vehicle_type      ENUM('MOTORBIKE','BICYCLE','CAR') NOT NULL,
                          license_plate     VARCHAR(20) NOT NULL,
                          is_online         BIT NOT NULL DEFAULT 0,
                          last_online_at    DATETIME(6)     NULL,                     -- [V2] online lần cuối lúc nào
                          avg_rating        DECIMAL(3,2) NOT NULL DEFAULT 0,
                          total_delivery    INT NOT NULL DEFAULT 0,
                          active_delivery   INT NOT NULL DEFAULT 0,                   -- [V2] đang giao bao nhiêu đơn
                          created_at        DATETIME(6),
                          updated_at        DATETIME(6),
                          PRIMARY KEY (shipper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ─── Unique constraints ───────────────────────────────────────

-- [V2] uk_carts_customer_restaurant thay cho uk_carts_customer
--      → 1 customer có thể có nhiều cart, nhưng mỗi restaurant chỉ 1 cart
ALTER TABLE carts                ADD CONSTRAINT uk_carts_customer_restaurant  UNIQUE (customer_id, restaurant_id);
ALTER TABLE conversations        ADD CONSTRAINT uk_conversation_pair          UNIQUE (user1_id, user2_id);
ALTER TABLE deliveries           ADD CONSTRAINT uk_deliveries_order           UNIQUE (order_id);
ALTER TABLE favorite_restaurants ADD CONSTRAINT uk_favorite                   UNIQUE (customer_id, restaurant_id);
ALTER TABLE payments             ADD CONSTRAINT uk_payments_order             UNIQUE (order_id);
ALTER TABLE reviews              ADD CONSTRAINT uk_reviews_order              UNIQUE (order_id);
ALTER TABLE users                ADD CONSTRAINT uk_users_phone                UNIQUE (phone);
ALTER TABLE users                ADD CONSTRAINT uk_users_email                UNIQUE (email);
ALTER TABLE users                ADD CONSTRAINT uk_users_google_id            UNIQUE (google_id);
ALTER TABLE shippers             ADD CONSTRAINT uk_shippers_user              UNIQUE (user_id);




-- ─── Indexes ──────────────────────────────────────────────────

CREATE INDEX idx_messages_conversation          ON messages         (conversation_id, created_at DESC);  -- [V2] thêm created_at

CREATE INDEX idx_notifications_user             ON notifications    (user_id);
CREATE INDEX idx_notifications_user_role        ON notifications    (user_id, recipient_role);  -- [V2] lọc theo role
CREATE INDEX idx_orders_customer                ON orders           (customer_id);
CREATE INDEX idx_orders_restaurant              ON orders           (restaurant_id);
CREATE INDEX idx_orders_shipper                 ON orders           (shipper_id);
CREATE INDEX idx_orders_status                  ON orders           (order_status);
CREATE INDEX idx_otps_phone_purpose             ON otps             (phone, purpose);
CREATE INDEX idx_reviews_restaurant             ON reviews          (restaurant_id);
CREATE INDEX idx_reviews_shipper                ON reviews          (shipper_id);                            -- [V2] tổng hợp rating shipper
CREATE INDEX idx_restaurant_registers_owner     ON restaurant_registers (owner_id);                         -- [V2] query đơn mới nhất theo owner
CREATE INDEX idx_shipper_registers_user         ON shipper_registers (user_id);                             -- [V2]
CREATE INDEX idx_transactions_user              ON transactions     (user_id);
CREATE INDEX idx_transactions_order             ON transactions     (order_id);
CREATE INDEX idx_carts_customer                 ON carts            (customer_id);                        -- [V2] tìm tất cả cart của 1 customer
CREATE INDEX idx_customer_addresses_customer    ON customer_addresses (customer_id);                     -- [V2]
CREATE INDEX idx_review_images_review           ON review_images    (review_id);                         -- [V2]


-- ─── Foreign keys ─────────────────────────────────────────────

ALTER TABLE cart_items              ADD CONSTRAINT fk_cart_items_cart               FOREIGN KEY (cart_id)           REFERENCES carts            (cart_id);
ALTER TABLE cart_items              ADD CONSTRAINT fk_cart_items_food               FOREIGN KEY (food_id)           REFERENCES foods            (food_id);
ALTER TABLE carts                   ADD CONSTRAINT fk_carts_customer                FOREIGN KEY (customer_id)       REFERENCES users            (user_id);
ALTER TABLE carts                   ADD CONSTRAINT fk_carts_restaurant              FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE categories              ADD CONSTRAINT fk_categories_restaurant         FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE conversations           ADD CONSTRAINT fk_conversations_user1           FOREIGN KEY (user1_id)          REFERENCES users            (user_id);
ALTER TABLE conversations           ADD CONSTRAINT fk_conversations_user2           FOREIGN KEY (user2_id)          REFERENCES users            (user_id);
ALTER TABLE customer_addresses      ADD CONSTRAINT fk_customer_addresses_user       FOREIGN KEY (customer_id)       REFERENCES users            (user_id) ON DELETE CASCADE;  -- [V2]

ALTER TABLE deliveries              ADD CONSTRAINT fk_deliveries_order              FOREIGN KEY (order_id)          REFERENCES orders           (order_id);
ALTER TABLE deliveries              ADD CONSTRAINT fk_deliveries_shipper            FOREIGN KEY (shipper_id)        REFERENCES users            (user_id);
ALTER TABLE favorite_restaurants    ADD CONSTRAINT fk_favorite_customer             FOREIGN KEY (customer_id)       REFERENCES users            (user_id);
ALTER TABLE favorite_restaurants    ADD CONSTRAINT fk_favorite_restaurant           FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE foods                   ADD CONSTRAINT fk_foods_category                FOREIGN KEY (category_id)       REFERENCES categories       (category_id);
ALTER TABLE foods                   ADD CONSTRAINT fk_foods_restaurant              FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE messages                ADD CONSTRAINT fk_messages_conversation         FOREIGN KEY (conversation_id)   REFERENCES conversations    (conversation_id) ON DELETE CASCADE;
ALTER TABLE messages                ADD CONSTRAINT fk_messages_sender               FOREIGN KEY (sender_id)         REFERENCES users            (user_id);
ALTER TABLE notifications           ADD CONSTRAINT fk_notifications_user            FOREIGN KEY (user_id)           REFERENCES users            (user_id);
ALTER TABLE order_items             ADD CONSTRAINT fk_order_items_food              FOREIGN KEY (food_id)           REFERENCES foods            (food_id);
ALTER TABLE order_items             ADD CONSTRAINT fk_order_items_order             FOREIGN KEY (order_id)          REFERENCES orders           (order_id);
ALTER TABLE orders                  ADD CONSTRAINT fk_orders_customer               FOREIGN KEY (customer_id)       REFERENCES users            (user_id);
ALTER TABLE orders                  ADD CONSTRAINT fk_orders_restaurant             FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE orders                  ADD CONSTRAINT fk_orders_shipper                FOREIGN KEY (shipper_id)        REFERENCES users            (user_id);
ALTER TABLE orders                  ADD CONSTRAINT fk_orders_address                FOREIGN KEY (address_id)        REFERENCES customer_addresses (address_id);
ALTER TABLE orders                  ADD CONSTRAINT fk_orders_cancelled_by           FOREIGN KEY (cancelled_by)      REFERENCES users            (user_id);  -- [V2]
ALTER TABLE payments                ADD CONSTRAINT fk_payments_order                FOREIGN KEY (order_id)          REFERENCES orders           (order_id);
ALTER TABLE payments                ADD CONSTRAINT fk_payments_confirmed_by         FOREIGN KEY (confirmed_by)      REFERENCES users            (user_id);
ALTER TABLE transactions            ADD CONSTRAINT fk_transactions_payment          FOREIGN KEY (payment_id)        REFERENCES payments         (payment_id);
ALTER TABLE reports                 ADD CONSTRAINT fk_reports_reporter              FOREIGN KEY (reporter_id)       REFERENCES users            (user_id);
ALTER TABLE restaurants             ADD CONSTRAINT fk_restaurants_owner             FOREIGN KEY (owner_id)          REFERENCES users            (user_id);
ALTER TABLE restaurant_registers    ADD CONSTRAINT fk_restaurant_registers_owner    FOREIGN KEY (owner_id)          REFERENCES users            (user_id);
ALTER TABLE review_images           ADD CONSTRAINT fk_review_images_review          FOREIGN KEY (review_id)         REFERENCES reviews          (review_id) ON DELETE CASCADE;  -- [V2]
ALTER TABLE reviews                 ADD CONSTRAINT fk_reviews_shipper               FOREIGN KEY (shipper_id)        REFERENCES shippers         (shipper_id);
ALTER TABLE reviews                 ADD CONSTRAINT fk_reviews_customer              FOREIGN KEY (customer_id)       REFERENCES users            (user_id);
ALTER TABLE reviews                 ADD CONSTRAINT fk_reviews_order                 FOREIGN KEY (order_id)          REFERENCES orders           (order_id);
ALTER TABLE reviews                 ADD CONSTRAINT fk_reviews_restaurant            FOREIGN KEY (restaurant_id)     REFERENCES restaurants      (restaurant_id);
ALTER TABLE shipper_registers       ADD CONSTRAINT fk_shipper_registers_user        FOREIGN KEY (user_id)           REFERENCES users            (user_id);
ALTER TABLE shippers                ADD CONSTRAINT fk_shippers_user                 FOREIGN KEY (user_id)           REFERENCES users            (user_id);
ALTER TABLE transactions            ADD CONSTRAINT fk_transactions_order            FOREIGN KEY (order_id)          REFERENCES orders           (order_id);
ALTER TABLE transactions            ADD CONSTRAINT fk_transactions_user             FOREIGN KEY (user_id)           REFERENCES users            (user_id);
ALTER TABLE users                   ADD CONSTRAINT fk_users_deleted_by              FOREIGN KEY (deleted_by)        REFERENCES users            (user_id);  -- [V2]





-- ─── Auto cleanup conversations ──────────────────────────────
--  Xóa conversation không có tin nhắn mới trong 3 ngày.
--  Messages tự xóa theo nhờ ON DELETE CASCADE trên fk_messages_conversation.
--  Bật MySQL Event Scheduler: SET GLOBAL event_scheduler = ON;

CREATE EVENT evt_cleanup_conversations
ON SCHEDULE EVERY 1 DAY
STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 2 HOUR)
DO
DELETE FROM conversations
WHERE last_message_at < NOW() - INTERVAL 3 DAY
   OR (last_message_at IS NULL AND created_at < NOW() - INTERVAL 3 DAY)
    LIMIT 500;

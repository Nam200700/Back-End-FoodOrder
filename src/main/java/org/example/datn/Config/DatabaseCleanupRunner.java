package org.example.datn.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseCleanupRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        log.info("Checking database for old unique constraints and foreign keys on carts table...");
        try {
            // 1. Find the database name (schema name)
            String schemaName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schemaName == null) {
                schemaName = "fresh_delivery";
            }
            log.info("Active database schema: {}", schemaName);

            // 2. Find any unique index on 'customer_id' column that is not 'PRIMARY' or the composite key
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM carts");
            String oldUniqueKeyName = null;
            for (Map<String, Object> index : indexes) {
                String keyName = (String) index.get("Key_name");
                String columnName = (String) index.get("Column_name");
                Long nonUnique = ((Number) index.get("Non_unique")).longValue();
                
                if (nonUnique == 0 && "customer_id".equalsIgnoreCase(columnName)) {
                    if (!"PRIMARY".equalsIgnoreCase(keyName) && !"uk_carts_customer_restaurant".equalsIgnoreCase(keyName)) {
                        oldUniqueKeyName = keyName;
                        break;
                    }
                }
            }

            if (oldUniqueKeyName != null) {
                log.info("Found legacy unique index '{}' on column 'customer_id'. Running cleanup sequence...", oldUniqueKeyName);

                // 3. Find the exact foreign key constraint name referencing 'users' on column 'customer_id'
                String fkQuery = "SELECT CONSTRAINT_NAME " +
                        "FROM information_schema.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_SCHEMA = ? " +
                        "  AND TABLE_NAME = 'carts' " +
                        "  AND COLUMN_NAME = 'customer_id' " +
                        "  AND REFERENCED_TABLE_NAME = 'users'";
                
                List<String> fkNames = jdbcTemplate.queryForList(fkQuery, String.class, schemaName);
                
                // 4. Drop the foreign key(s)
                for (String fkName : fkNames) {
                    try {
                        log.info("Dropping foreign key '{}' from carts table...", fkName);
                        jdbcTemplate.execute("ALTER TABLE carts DROP FOREIGN KEY " + fkName);
                        log.info("Successfully dropped foreign key '{}'.", fkName);
                    } catch (Exception e) {
                        log.warn("Failed to drop foreign key '{}': {}", fkName, e.getMessage());
                    }
                }

                // 5. Drop the legacy unique index
                try {
                    jdbcTemplate.execute("ALTER TABLE carts DROP INDEX " + oldUniqueKeyName);
                    log.info("Successfully dropped legacy unique index '{}'.", oldUniqueKeyName);
                } catch (Exception e) {
                    log.error("Failed to drop unique index '{}': {}", oldUniqueKeyName, e.getMessage());
                }

                // 6. Re-create the standard foreign key without the unique constraint
                try {
                    jdbcTemplate.execute("ALTER TABLE carts ADD CONSTRAINT fk_carts_customer FOREIGN KEY (customer_id) REFERENCES users (user_id)");
                    log.info("Successfully re-created foreign key 'fk_carts_customer'.");
                } catch (Exception e) {
                    log.error("Failed to re-create foreign key 'fk_carts_customer': {}", e.getMessage());
                }

                // 7. Ensure a non-unique index exists for foreign key performance
                try {
                    jdbcTemplate.execute("CREATE INDEX idx_carts_customer ON carts (customer_id)");
                    log.info("Created standard non-unique index 'idx_carts_customer'.");
                } catch (Exception e) {
                    log.debug("Index 'idx_carts_customer' might already exist: {}", e.getMessage());
                }
            } else {
                log.info("No old unique constraint on column 'customer_id' found in carts table.");
            }

            // 8. Ensure 'otps' table 'phone' column can store email (VARCHAR(100))
            try {
                log.info("Ensuring otps.phone column is VARCHAR(100) for email OTP compatibility...");
                jdbcTemplate.execute("ALTER TABLE otps MODIFY COLUMN phone VARCHAR(100) NOT NULL");
                log.info("Successfully checked/altered otps.phone column to VARCHAR(100).");
            } catch (Exception e) {
                log.error("Failed to alter otps.phone column: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error during database constraint cleanup: {}", e.getMessage(), e);
        }
    }
}

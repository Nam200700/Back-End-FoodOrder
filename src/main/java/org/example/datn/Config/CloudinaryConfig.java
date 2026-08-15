package org.example.datn.Config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", decodeIfBase64(cloudName),
            "api_key", decodeIfBase64(apiKey),
            "api_secret", decodeIfBase64(apiSecret)
        ));
    }

    private String decodeIfBase64(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        try {
            String trimmed = value.trim();
            if (trimmed.matches("^[a-zA-Z0-9+/]*={0,2}$") && trimmed.length() % 4 == 0) {
                byte[] decoded = java.util.Base64.getDecoder().decode(trimmed);
                String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                if (decodedStr.chars().allMatch(c -> c >= 32 && c < 127)) {
                    return decodedStr;
                }
            }
            return value;
        } catch (Exception e) {
            return value;
        }
    }
}

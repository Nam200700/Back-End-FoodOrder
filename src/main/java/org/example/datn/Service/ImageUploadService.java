package org.example.datn.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final Cloudinary cloudinary;

    public String uploadImage(MultipartFile file) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "fresh_delivery"
        ));
        return uploadResult.get("secure_url").toString();
    }

    /**
     * Trích xuất public_id của ảnh từ URL Cloudinary.
     * Ví dụ: https://res.cloudinary.com/djp3z7h8j/image/upload/v1683902342/fresh_delivery/a1b2c3d4e5.png
     * Kết quả trả về: fresh_delivery/a1b2c3d4e5
     */
    public String extractPublicId(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains("res.cloudinary.com")) {
            return null;
        }
        try {
            int uploadIndex = imageUrl.indexOf("/image/upload/");
            if (uploadIndex == -1) {
                return null;
            }
            String path = imageUrl.substring(uploadIndex + "/image/upload/".length());

            // Loại bỏ phần version (ví dụ: v1683902342/)
            if (path.startsWith("v")) {
                int firstSlash = path.indexOf("/");
                if (firstSlash != -1) {
                    String versionPart = path.substring(1, firstSlash);
                    if (versionPart.matches("\\d+")) {
                        path = path.substring(firstSlash + 1);
                    }
                }
            }

            // Loại bỏ phần mở rộng ở cuối (.png, .jpg, .webp...)
            int lastDotIndex = path.lastIndexOf(".");
            if (lastDotIndex != -1) {
                path = path.substring(0, lastDotIndex);
            }

            return path;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Thực hiện xóa ảnh cũ trên Cloudinary (Best-effort, không ném exception làm rollback transaction chính).
     */
    public void deleteImage(String imageUrl) {
        String publicId = extractPublicId(imageUrl);
        if (publicId != null) {
            try {
                Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                System.out.println("[CLOUDINARY] Destroy result for " + publicId + ": " + result);
            } catch (Exception e) {
                System.err.println("[CLOUDINARY] Failed to destroy image: " + publicId + ", error: " + e.getMessage());
            }
        }
    }
}
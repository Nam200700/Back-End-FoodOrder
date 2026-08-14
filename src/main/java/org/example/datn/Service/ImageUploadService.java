package org.example.datn.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final Cloudinary cloudinary;

    /**
     * Upload ảnh có NÉN ngay lúc nhận: giới hạn cạnh dài tối đa 1600px (crop "limit" → chỉ thu nhỏ,
     * không phóng to/cắt) + chất lượng "auto:good". Ảnh chụp điện thoại thường 3–5MB/4000px, sau khi
     * nén còn vài trăm KB → trang khách tải nhanh hơn, tiết kiệm băng thông & dung lượng lưu trữ.
     */
    public String uploadImage(MultipartFile file) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "fresh_delivery",
                "transformation", new Transformation()
                        .width(1600).height(1600).crop("limit")
                        .quality("auto:good")
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
     * Xoá ảnh cũ trên Cloudinary — chạy BẤT ĐỒNG BỘ (fire-and-forget) trên pool "imageExecutor".
     * Best-effort: nuốt mọi lỗi, không ném ra để khỏi rollback transaction chính. Nhờ async, request
     * cập nhật (avatar/món/quán) trả về ngay, không chờ round-trip destroy tới Cloudinary.
     */
    @Async("imageExecutor")
    public void deleteImage(String imageUrl) {
        String publicId = extractPublicId(imageUrl);
        if (publicId != null) {
            try {
                Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.debug("[CLOUDINARY] Destroy result for {}: {}", publicId, result);
            } catch (Exception e) {
                log.warn("[CLOUDINARY] Failed to destroy image {}: {}", publicId, e.getMessage());
            }
        }
    }
}
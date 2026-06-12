package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.Service.ImageUploadService;
import org.example.datn.Exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageUploadService imageUploadService;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<?>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "File tải lên không được để trống."));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "Kích thước file vượt quá giới hạn cho phép (tối đa 5MB)."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, "Định dạng file không hợp lệ. Chỉ hỗ trợ các định dạng hình ảnh JPEG, PNG, WEBP, HEIC, HEIF."));
        }

        try {
            String url = imageUploadService.uploadImage(file);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Lỗi khi tải ảnh lên Cloudinary: " + e.getMessage()));
        }
    }
}
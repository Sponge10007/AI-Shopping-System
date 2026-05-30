package com.aishop.modules.upload;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.upload.dto.UploadResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/product-images")
    public ApiResponse<UploadResponse> uploadProductImage(@RequestPart("image") MultipartFile image) {
        return ApiResponse.ok(uploadService.uploadProductImage(image));
    }

    @PostMapping("/search-images")
    public ApiResponse<UploadResponse> uploadSearchImage(@RequestPart("image") MultipartFile image) {
        return ApiResponse.ok(uploadService.uploadSearchImage(image));
    }
}


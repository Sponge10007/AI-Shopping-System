package com.aishop.modules.upload;

import com.aishop.modules.upload.dto.UploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class UploadService {

    public UploadResponse uploadProductImage(MultipartFile file) {
        String fileId = "img_" + UUID.randomUUID();
        return new UploadResponse(fileId, "https://example.com/uploads/products/" + fileId + ".jpg", "PRODUCT_IMAGE");
    }

    public UploadResponse uploadSearchImage(MultipartFile file) {
        String fileId = "search_" + UUID.randomUUID();
        return new UploadResponse(fileId, "https://example.com/uploads/search/" + fileId + ".jpg", "SEARCH_IMAGE");
    }
}


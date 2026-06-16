package com.aishop;

import com.aishop.modules.upload.UploadService;
import com.aishop.modules.upload.dto.UploadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class UploadServiceTest {
    private final UploadService uploadService = new UploadService();

    @Test
    void productImageUploadCurrentlyDoesNotValidateContentTypeOrFileHeader() {
        MockMultipartFile textFile = new MockMultipartFile(
                "image",
                "payload.txt",
                "text/plain",
                "not an image".getBytes()
        );

        UploadResponse response = uploadService.uploadProductImage(textFile);

        assertThat(response.fileId()).startsWith("img_");
        assertThat(response.url()).contains("/uploads/products/");
        assertThat(response.purpose()).isEqualTo("PRODUCT_IMAGE");
    }
}

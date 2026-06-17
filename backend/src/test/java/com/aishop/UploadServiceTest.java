package com.aishop;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.modules.upload.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadServiceTest {
    private final UploadService uploadService = new UploadService();

    @Test
    void productImageUploadRejectsNonImageContentType() {
        MockMultipartFile textFile = new MockMultipartFile(
                "image",
                "payload.txt",
                "text/plain",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> uploadService.uploadProductImage(textFile))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    org.assertj.core.api.Assertions.assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
                });
    }
}

package com.aishop.modules.upload.dto;

public record UploadResponse(
        String fileId,
        String url,
        String purpose
) {
}


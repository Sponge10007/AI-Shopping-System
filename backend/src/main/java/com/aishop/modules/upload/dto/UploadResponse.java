package com.aishop.modules.upload.dto;

/**
 * 文件上传响应
 *
 * 字段说明：
 * - fileId: 文件唯一标识（UUID）
 * - url: 文件访问 URL（相对路径，如 /uploads/images/products/2026/06/uuid.jpg）
 * - filename: 原始文件名（用于前端展示）
 * - size: 文件大小（字节）
 */
public record UploadResponse(
        String fileId,
        String url,
        String filename,
        long size
) {
}

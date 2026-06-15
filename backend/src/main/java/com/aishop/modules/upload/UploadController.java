package com.aishop.modules.upload;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.upload.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 *
 * 接口说明：
 * - POST /api/v1/uploads/product-images — 上传商品图片（需要 MERCHANT 角色）
 * - POST /api/v1/uploads/search-images — 上传搜索图片（需要登录）
 *
 * 权限校验：
 * - product-images: 仅 MERCHANT 角色可上传
 * - search-images: 登录用户可上传
 *
 * 技术要点：
 * - 使用 @RequestPart 接收 MultipartFile
 * - 角色校验在 Controller 层完成，Service 层只关注业务逻辑
 *
 * 修复记录（2026-06-08 v2）：
 * - 增强 @AuthenticationPrincipal 解析失败的兜底处理
 * - 增加日志记录，便于排查 500 错误
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 上传商品图片
     *
     * 权限要求：MERCHANT 角色
     * 测试用例 TC-UP001: 商家正常上传 → 200
     * 测试用例 TC-UP004: CUSTOMER 上传 → 403
     */
    @PostMapping("/product-images")
    public ApiResponse<UploadResponse> uploadProductImage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestPart("image") MultipartFile image) {
        // 校验登录：@AuthenticationPrincipal 可能因类型转换失败返回 null
        if (currentUser == null) {
            log.warn("上传商品图片失败: @AuthenticationPrincipal 解析为 null，可能缺少 Token 或 Token 无效");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        log.debug("上传商品图片: userId={}, role={}, fileSize={}",
                currentUser.userId(), currentUser.role(),
                image != null ? image.getSize() : 0);
        // 校验角色：仅 MERCHANT 可上传商品图片
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以上传商品图片");
        }
        return ApiResponse.ok(uploadService.uploadProductImage(image));
    }

    /**
     * 上传搜索图片
     *
     * 权限要求：登录用户
     * 测试用例 TC-UP002: 登录用户正常上传 → 200
     * 测试用例 TC-UP003: 未登录上传 → 401（由 JWT 过滤器处理）
     */
    @PostMapping("/search-images")
    public ApiResponse<UploadResponse> uploadSearchImage(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestPart("image") MultipartFile image) {
        // 校验登录
        if (currentUser == null) {
            log.warn("上传搜索图片失败: @AuthenticationPrincipal 解析为 null");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        log.debug("上传搜索图片: userId={}, role={}, fileSize={}",
                currentUser.userId(), currentUser.role(),
                image != null ? image.getSize() : 0);
        return ApiResponse.ok(uploadService.uploadSearchImage(image));
    }
}

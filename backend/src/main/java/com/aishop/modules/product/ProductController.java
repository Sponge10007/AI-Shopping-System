package com.aishop.modules.product;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.product.dto.RestockRequest;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品控制器
 *
 * 接口路径说明：
 * - /api/v1/products/** — 公开接口（无需登录，可查询在售商品）
 * - /api/v1/merchant/products/** — 商家接口（需要登录，管理自己的商品）
 *
 * 认证方式：
 * - 公开接口：JWT 过滤器白名单，无需 Token
 * - 商家接口：需要 Bearer Token，从 SecurityContext 获取当前用户
 *
 * 权限校验：
 * - 所有商家接口（/api/v1/merchant/products/**）需要 MERCHANT 角色
 * - CUSTOMER 角色访问商家接口应返回 403
 */
@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ==================== 公开接口 ====================

    /**
     * 查询在售商品列表（公开）
     *
     * 支持分页、分类筛选、排序
     */
    @GetMapping("/api/v1/products")
    public ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return ApiResponse.ok(productService.listProducts(page, size, categoryId, sortBy, sortOrder));
    }

    /**
     * 获取商品详情（公开）
     */
    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String productId) {
        return ApiResponse.ok(productService.getProduct(productId));
    }

    // ==================== 商家接口 ====================

    /**
     * 商家查询自己的商品列表
     */
    @GetMapping("/api/v1/merchant/products")
    public ApiResponse<PageResponse<ProductResponse>> listMerchantProducts(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 未认证时返回 401
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 校验角色必须为 MERCHANT
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以查看商品列表");
        }
        return ApiResponse.ok(productService.listMerchantProducts(currentUser, status, page, size));
    }

    /**
     * 商家创建商品
     */
    @PostMapping("/api/v1/merchant/products")
    public ApiResponse<ProductMutationResponse> createProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateProductRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 校验角色必须为 MERCHANT
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以创建商品");
        }
        return ApiResponse.ok(productService.createProduct(currentUser, request));
    }

    /**
     * 商家更新商品（PATCH 语义）
     */
    @PatchMapping("/api/v1/merchant/products/{productId}")
    public ApiResponse<ProductResponse> updateProduct(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String productId,
            @RequestBody UpdateProductRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 校验角色必须为 MERCHANT
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以更新商品");
        }
        return ApiResponse.ok(productService.updateProduct(currentUser, productId, request));
    }

    /**
     * 商家下架商品（逻辑删除）
     */
    @DeleteMapping("/api/v1/merchant/products/{productId}")
    public ApiResponse<ProductMutationResponse> offSale(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String productId) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 校验角色必须为 MERCHANT
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以下架商品");
        }
        return ApiResponse.ok(productService.offSale(currentUser, productId));
    }

    /**
     * 商家补货
     */
    @PostMapping("/api/v1/merchant/products/{productId}/restock")
    public ApiResponse<RestockResponse> restock(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String productId,
            @Valid @RequestBody RestockRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        // 校验角色必须为 MERCHANT
        if (!"MERCHANT".equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅商家可以补货");
        }
        return ApiResponse.ok(productService.restock(currentUser, productId, request));
    }
}

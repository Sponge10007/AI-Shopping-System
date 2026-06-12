package com.aishop.modules.product;

import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.product.dto.RestockRequest;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/v1/products")
    public ApiResponse<PageResponse<ProductSummaryResponse>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(productService.listProducts(page, size));
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable String productId) {
        return ApiResponse.ok(productService.getProduct(productId));
    }

    @GetMapping("/api/v1/merchant/products")
    public ApiResponse<PageResponse<ProductResponse>> listMerchantProducts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(productService.listMerchantProducts(status, page, size));
    }

    @PostMapping("/api/v1/merchant/products")
    public ApiResponse<ProductMutationResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(productService.createProduct(request));
    }

    @PatchMapping("/api/v1/merchant/products/{productId}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable String productId,
            @RequestBody UpdateProductRequest request
    ) {
        return ApiResponse.ok(productService.updateProduct(productId, request));
    }

    @PostMapping("/api/v1/merchant/products/{productId}/restock")
    public ApiResponse<RestockResponse> restock(
            @PathVariable String productId,
            @Valid @RequestBody RestockRequest request
    ) {
        return ApiResponse.ok(productService.restock(productId, request));
    }

    @DeleteMapping("/api/v1/merchant/products/{productId}")
    public ApiResponse<ProductMutationResponse> offSale(@PathVariable String productId) {
        return ApiResponse.ok(productService.offSale(productId));
    }
}


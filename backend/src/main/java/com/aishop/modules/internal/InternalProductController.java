package com.aishop.modules.internal;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.internal.dto.BatchProductAiSummaryRequest;
import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/products")
public class InternalProductController {
    private final InternalProductService internalProductService;

    public InternalProductController(InternalProductService internalProductService) {
        this.internalProductService = internalProductService;
    }

    @GetMapping("/{productId}/ai-summary")
    public ApiResponse<ProductAiSummaryResponse> getAiSummary(@PathVariable String productId) {
        return ApiResponse.ok(internalProductService.getAiSummary(productId));
    }

    @PostMapping("/ai-summaries")
    public ApiResponse<BatchProductAiSummaryResponse> getAiSummaries(
            @Valid @RequestBody BatchProductAiSummaryRequest request
    ) {
        return ApiResponse.ok(internalProductService.getAiSummaries(request.productIds()));
    }
}


package com.aishop.modules.ai;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.ai.dto.CompareProductsRequest;
import com.aishop.modules.ai.dto.CompareProductsResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/compare")
public class AiCompareController {
    private final AiCompareService aiCompareService;

    public AiCompareController(AiCompareService aiCompareService) {
        this.aiCompareService = aiCompareService;
    }

    @PostMapping
    public ApiResponse<CompareProductsResponse> compare(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CompareProductsRequest request
    ) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(aiCompareService.compare(currentUser, request));
    }
}

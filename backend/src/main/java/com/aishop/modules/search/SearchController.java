package com.aishop.modules.search;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/semantic")
    public ApiResponse<SemanticSearchResponse> semanticSearch(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody SemanticSearchRequest request) {
        requireUser(currentUser);
        return ApiResponse.ok(searchService.semanticSearch(currentUser, request));
    }

    @PostMapping("/image")
    public ApiResponse<ImageSearchResponse> imageSearch(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestPart("image") MultipartFile image,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        requireUser(currentUser);
        return ApiResponse.ok(searchService.imageSearch(currentUser, image, limit));
    }

    private void requireUser(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }
}


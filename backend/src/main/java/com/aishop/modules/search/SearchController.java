package com.aishop.modules.search;

import com.aishop.common.response.ApiResponse;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import jakarta.validation.Valid;
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
    public ApiResponse<SemanticSearchResponse> semanticSearch(@Valid @RequestBody SemanticSearchRequest request) {
        return ApiResponse.ok(searchService.semanticSearch(request));
    }

    @PostMapping("/image")
    public ApiResponse<ImageSearchResponse> imageSearch(
            @RequestPart("image") MultipartFile image,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.ok(searchService.imageSearch(image, limit));
    }
}


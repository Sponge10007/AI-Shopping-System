package com.aishop.modules.search;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiImageSearchResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SearchFilters;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {
    private final ProductService productService;
    private final AiServiceClient aiServiceClient;
    private final BehaviorService behaviorService;

    public SearchService(
            ProductService productService,
            AiServiceClient aiServiceClient,
            BehaviorService behaviorService
    ) {
        this.productService = productService;
        this.aiServiceClient = aiServiceClient;
        this.behaviorService = behaviorService;
    }

    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        String userId = CurrentUser.prototypeCustomer().userId();
        behaviorService.recordForUser(userId, new BehaviorEventRequest(
                "SEARCH",
                null,
                request.query(),
                Map.of("source", "semantic_search")
        ));

        int limit = normalizeLimit(request.limit(), 20);
        List<String> aiProductIds = aiServiceClient.semanticSearch(
                userId,
                request.query(),
                request.distanceThreshold(),
                Math.max(limit, 50)
        );
        List<ProductSummaryResponse> items = applyFilters(
                productService.findSummariesByIds(aiProductIds, 0.93, "语义匹配你的搜索意图"),
                request.filters()
        ).stream().limit(limit).toList();

        boolean relaxed = false;
        if (items.isEmpty()) {
            relaxed = true;
            items = applyFilters(
                    productService.topSellingSummaries(limit, 0.70, "AI 暂无精确结果，已为你返回热门商品"),
                    request.filters()
            ).stream().limit(limit).toList();
        }

        return new SemanticSearchResponse(
                request.query(),
                relaxed,
                items
        );
    }

    public ImageSearchResponse imageSearch(MultipartFile image, Integer limit) {
        String userId = CurrentUser.prototypeCustomer().userId();
        int normalizedLimit = normalizeLimit(limit, 20);
        String imagePathOrUrl = "search-upload://" + image.getOriginalFilename();
        behaviorService.recordForUser(userId, new BehaviorEventRequest(
                "IMAGE_SEARCH",
                null,
                null,
                Map.of(
                        "filename", image.getOriginalFilename() == null ? "" : image.getOriginalFilename(),
                        "size", image.getSize()
                )
        ));

        AiImageSearchResult aiResult = aiServiceClient.imageSearch(
                userId,
                imagePathOrUrl,
                normalizedLimit,
                0.9
        );
        List<ProductSummaryResponse> items = productService.findSummariesByIds(
                        aiResult.productIds(),
                        0.88,
                        "外观与上传图片相似"
                )
                .stream()
                .limit(normalizedLimit)
                .toList();
        if (items.isEmpty()) {
            items = productService.topSellingSummaries(
                    normalizedLimit,
                    0.65,
                    "图片搜索暂不可用，已为你返回热门商品"
            );
        }

        return new ImageSearchResponse(
                detectedObject(aiResult),
                items
        );
    }

    private List<ProductSummaryResponse> applyFilters(List<ProductSummaryResponse> items, SearchFilters filters) {
        if (filters == null) {
            return items;
        }
        return items.stream()
                .filter(item -> !Boolean.TRUE.equals(filters.inStock()) || item.stock() > 0)
                .filter(item -> priceAtLeast(item, filters.minPrice()))
                .filter(item -> priceAtMost(item, filters.maxPrice()))
                .toList();
    }

    private boolean priceAtLeast(ProductSummaryResponse item, BigDecimal minPrice) {
        return minPrice == null || new BigDecimal(item.price()).compareTo(minPrice) >= 0;
    }

    private boolean priceAtMost(ProductSummaryResponse item, BigDecimal maxPrice) {
        return maxPrice == null || new BigDecimal(item.price()).compareTo(maxPrice) <= 0;
    }

    private String detectedObject(AiImageSearchResult aiResult) {
        if (aiResult.keywords() == null || aiResult.keywords().isEmpty()) {
            return "未知商品";
        }
        return aiResult.keywords().get(0);
    }

    private int normalizeLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, 100);
    }
}

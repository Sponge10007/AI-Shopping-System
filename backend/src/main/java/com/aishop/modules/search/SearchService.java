package com.aishop.modules.search;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SearchService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

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

    public SemanticSearchResponse semanticSearch(CurrentUser currentUser, SemanticSearchRequest request) {
        String userId = currentUser.userId();
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

    public ImageSearchResponse imageSearch(CurrentUser currentUser, MultipartFile image, Integer limit) {
        String userId = currentUser.userId();
        int normalizedLimit = normalizeLimit(limit, 20);
        String imageDataUrl = toImageDataUrl(image);
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
                imageDataUrl,
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

    private String toImageDataUrl(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "搜索图片不能为空");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "搜索图片不能超过10MB");
        }

        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "图片搜索仅支持 JPEG、PNG、WebP 格式"
            );
        }

        try {
            byte[] content = image.getBytes();
            if (!matchesImageSignature(contentType, content)) {
                throw new BusinessException(
                        ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "图片内容与声明格式不匹配"
                );
            }
            return "data:" + contentType.toLowerCase() + ";base64,"
                    + Base64.getEncoder().encodeToString(content);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取搜索图片失败");
        }
    }

    private boolean matchesImageSignature(String contentType, byte[] content) {
        if (content.length < 4) {
            return false;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" ->
                    content[0] == (byte) 0xFF
                            && content[1] == (byte) 0xD8
                            && content[2] == (byte) 0xFF;
            case "image/png" ->
                    content[0] == (byte) 0x89
                            && content[1] == 0x50
                            && content[2] == 0x4E
                            && content[3] == 0x47;
            case "image/webp" ->
                    content.length >= 12
                            && content[0] == 0x52
                            && content[1] == 0x49
                            && content[2] == 0x46
                            && content[3] == 0x46
                            && content[8] == 0x57
                            && content[9] == 0x45
                            && content[10] == 0x42
                            && content[11] == 0x50;
            default -> false;
        };
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

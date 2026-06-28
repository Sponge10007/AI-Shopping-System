package com.aishop.modules.search;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiImageSearchResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SearchFilters;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class SearchService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final int SEARCH_CATALOG_LIMIT = 500;
    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:元)?\\s*(?:到|至|~|～|-)\\s*(\\d+(?:\\.\\d+)?)\\s*元?"
    );
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "(?:预算|价格|价位|不超过|低于|少于|控制在|最多)?\\s*(\\d+(?:\\.\\d+)?)\\s*元?\\s*(?:以内|以下|之内|封顶|左右)?"
    );
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "c_headphone", "耳机",
            "c_phone", "手机",
            "c_computer", "电脑",
            "c_accessory", "配件",
            "c_home", "家居",
            "c_food", "食品",
            "c_clothing", "服装",
            "c_books", "图书",
            "c_outdoor", "户外"
    );
    private static final List<String> FEATURE_KEYWORDS = List.of(
            "降噪", "通勤", "学习", "办公", "便携", "防漏", "温度显示", "运动",
            "跑步", "骑行", "健身", "收纳", "防泼水", "真无线", "蓝牙", "开放式",
            "稳固", "机械键盘", "紧凑", "人体工学", "静音", "护眼", "阅读",
            "调光", "防晒", "徒步", "轻量", "长续航", "充电盒", "触控"
    );
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    static {
        CATEGORY_KEYWORDS.put("c_headphone", List.of(
                "耳机", "降噪", "入耳", "头戴", "真无线", "开放式耳机"
        ));
        CATEGORY_KEYWORDS.put("c_accessory", List.of(
                "键盘", "鼠标", "机械键盘", "无线鼠标"
        ));
        CATEGORY_KEYWORDS.put("c_home", List.of(
                "保温杯", "杯子", "水杯", "喝水", "热水", "台灯", "桌灯", "阅读灯", "看书"
        ));
        CATEGORY_KEYWORDS.put("c_outdoor", List.of(
                "背包", "双肩包", "运动包"
        ));
        CATEGORY_KEYWORDS.put("c_clothing", List.of(
                "冲锋衣", "外套", "防晒衣", "衣服", "服装"
        ));
        CATEGORY_KEYWORDS.put("c_phone", List.of(
                "手机", "智能手机"
        ));
        CATEGORY_KEYWORDS.put("c_computer", List.of(
                "电脑", "笔记本", "台式机"
        ));
        CATEGORY_KEYWORDS.put("c_food", List.of(
                "食品", "零食", "饮料"
        ));
        CATEGORY_KEYWORDS.put("c_books", List.of(
                "图书", "书籍", "教材"
        ));
    }

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
        SearchIntent intent = analyzeIntent(request.query(), request.filters());
        List<ProductResponse> catalog = productService.searchableProducts(SEARCH_CATALOG_LIMIT);
        List<ProductSummaryResponse> items = rankProducts(
                request.query(),
                intent,
                catalog,
                aiProductIds,
                limit
        );

        boolean relaxed = false;
        if (items.isEmpty()) {
            relaxed = true;
            // 用户明确指定了商品品类或预算时，宁可诚实返回空结果，也不推荐不相关商品。
            if (!intent.hasHardConstraint()) {
                items = applyFilters(
                        productService.topSellingSummaries(limit, 0.70, "没有找到精确匹配，以下是当前热门商品"),
                        request.filters()
                ).stream().limit(limit).toList();
            }
        }

        return new SemanticSearchResponse(
                request.query(),
                relaxed,
                items
        );
    }

    private SearchIntent analyzeIntent(String query, SearchFilters requestFilters) {
        String normalized = normalize(query);
        String inferredCategory = inferCategory(normalized);
        ProductType productType = ProductType.infer(normalized);
        BigDecimal inferredMinPrice = null;
        BigDecimal inferredMaxPrice = null;

        Matcher rangeMatcher = PRICE_RANGE_PATTERN.matcher(normalized);
        if (rangeMatcher.find()) {
            inferredMinPrice = new BigDecimal(rangeMatcher.group(1));
            inferredMaxPrice = new BigDecimal(rangeMatcher.group(2));
            if (inferredMinPrice.compareTo(inferredMaxPrice) > 0) {
                BigDecimal swap = inferredMinPrice;
                inferredMinPrice = inferredMaxPrice;
                inferredMaxPrice = swap;
            }
        } else {
            Matcher maxMatcher = MAX_PRICE_PATTERN.matcher(normalized);
            while (maxMatcher.find()) {
                String fullMatch = maxMatcher.group();
                if (containsAny(fullMatch, List.of(
                        "预算", "价格", "价位", "不超过", "低于", "少于",
                        "控制在", "最多", "以内", "以下", "之内", "封顶", "左右"
                ))) {
                    inferredMaxPrice = new BigDecimal(maxMatcher.group(1));
                    break;
                }
            }
        }

        String categoryId = requestFilters != null && hasText(requestFilters.categoryId())
                ? requestFilters.categoryId()
                : inferredCategory;
        BigDecimal minPrice = requestFilters != null && requestFilters.minPrice() != null
                ? requestFilters.minPrice()
                : inferredMinPrice;
        BigDecimal maxPrice = requestFilters != null && requestFilters.maxPrice() != null
                ? requestFilters.maxPrice()
                : inferredMaxPrice;
        boolean inStock = requestFilters == null
                || requestFilters.inStock() == null
                || requestFilters.inStock();

        return new SearchIntent(categoryId, productType, minPrice, maxPrice, inStock);
    }

    private String inferCategory(String query) {
        return CATEGORY_KEYWORDS.entrySet().stream()
                .filter(entry -> containsAny(query, entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<ProductSummaryResponse> rankProducts(
            String query,
            SearchIntent intent,
            List<ProductResponse> catalog,
            List<String> semanticIds,
            int limit
    ) {
        String normalizedQuery = normalizeSynonyms(query);
        Map<String, Integer> semanticRanks = new LinkedHashMap<>();
        IntStream.range(0, semanticIds == null ? 0 : semanticIds.size())
                .forEach(index -> semanticRanks.putIfAbsent(semanticIds.get(index), index));

        return catalog.stream()
                .filter(product -> matchesHardConstraints(product, intent))
                .map(product -> scoreProduct(product, normalizedQuery, intent, semanticRanks))
                .filter(ranked -> ranked.relevance() > 0 || semanticRanks.containsKey(ranked.product().productId()))
                .sorted(Comparator.comparingDouble(RankedProduct::score).reversed()
                        .thenComparing(ranked -> ranked.product().sales(), Comparator.reverseOrder())
                        .thenComparing(ranked -> ranked.product().rating(), Comparator.reverseOrder()))
                .limit(limit)
                .map(this::toSearchSummary)
                .toList();
    }

    private boolean matchesHardConstraints(ProductResponse product, SearchIntent intent) {
        if (intent.inStock() && product.stock() <= 0) {
            return false;
        }
        if (hasText(intent.categoryId()) && !intent.categoryId().equals(product.categoryId())) {
            return false;
        }
        if (intent.productType() != null && !intent.productType().matches(product)) {
            return false;
        }

        BigDecimal price = new BigDecimal(product.price());
        return (intent.minPrice() == null || price.compareTo(intent.minPrice()) >= 0)
                && (intent.maxPrice() == null || price.compareTo(intent.maxPrice()) <= 0);
    }

    private RankedProduct scoreProduct(
            ProductResponse product,
            String query,
            SearchIntent intent,
            Map<String, Integer> semanticRanks
    ) {
        String productText = normalizeSynonyms(String.join(" ",
                safe(product.name()),
                safe(product.categoryName()),
                safe(product.description()),
                product.tags() == null ? "" : String.join(" ", product.tags())
        ));
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        double relevance = 0;

        if (query.contains(normalize(product.name()))) {
            relevance += 55;
            matches.add(product.name());
        }

        for (String feature : FEATURE_KEYWORDS) {
            String normalizedFeature = normalizeSynonyms(feature);
            if (query.contains(normalizedFeature) && productText.contains(normalizedFeature)) {
                relevance += feature.length() >= 3 ? 12 : 8;
                matches.add(feature);
            }
        }

        if (product.tags() != null) {
            for (String tag : product.tags()) {
                String normalizedTag = normalizeSynonyms(tag);
                if (!normalizedTag.isBlank()
                        && query.contains(normalizedTag)
                        && !matches.contains(tag)) {
                    relevance += 10;
                    matches.add(tag);
                }
            }
        }

        if (hasText(intent.categoryId())) {
            relevance += 42;
            matches.add(CATEGORY_LABELS.getOrDefault(intent.categoryId(), product.categoryName()));
        }

        Integer semanticRank = semanticRanks.get(product.productId());
        double semanticScore = semanticRank == null ? 0 : Math.max(4, 24 - semanticRank * 4);
        double qualityScore = product.rating() * 2 + Math.log10(Math.max(1, product.sales()) + 1) * 2;
        double score = relevance + semanticScore + qualityScore;

        return new RankedProduct(product, score, relevance, matches.stream().limit(4).toList());
    }

    private ProductSummaryResponse toSearchSummary(RankedProduct ranked) {
        ProductResponse product = ranked.product();
        double normalizedScore = BigDecimal.valueOf(
                        Math.min(0.99, Math.max(0.55, 0.55 + ranked.score() / 220.0))
                )
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        String reason = buildReason(ranked);

        return new ProductSummaryResponse(
                product.productId(),
                product.name(),
                product.price(),
                product.stock(),
                product.imageUrls() == null || product.imageUrls().isEmpty()
                        ? null
                        : product.imageUrls().get(0),
                product.detailUrl(),
                product.sales(),
                product.rating(),
                product.tags(),
                normalizedScore,
                reason
        );
    }

    private String buildReason(RankedProduct ranked) {
        List<String> matches = ranked.matches();
        if (!matches.isEmpty()) {
            return "匹配你的需求：" + String.join("、", matches);
        }
        return "结合语义相似度、用户评分和销量排序";
    }

    private boolean containsAny(String value, List<String> candidates) {
        return candidates.stream().anyMatch(value::contains);
    }

    private String normalizeSynonyms(String value) {
        return normalize(value)
                .replace("杯子", "保温杯")
                .replace("水杯", "保温杯")
                .replace("喝热水", "保温")
                .replace("热水", "保温")
                .replace("看书", "阅读")
                .replace("桌灯", "台灯")
                .replace("阅读灯", "台灯")
                .replace("防晒衣", "冲锋衣")
                .replace("双肩包", "背包");
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[\\s，。！？、,.!?；;：:（）()【】\\[\\]]+", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
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

    private record SearchIntent(
            String categoryId,
            ProductType productType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean inStock
    ) {
        boolean hasHardConstraint() {
            return categoryId != null || productType != null || minPrice != null || maxPrice != null;
        }
    }

    private record RankedProduct(
            ProductResponse product,
            double score,
            double relevance,
            List<String> matches
    ) {
    }

    private enum ProductType {
        KEYBOARD(List.of("键盘"), List.of("键盘")),
        MOUSE(List.of("鼠标"), List.of("鼠标")),
        CUP(List.of("保温杯", "杯子", "水杯", "喝水", "热水"), List.of("杯")),
        LAMP(List.of("台灯", "桌灯", "阅读灯", "看书"), List.of("灯")),
        BACKPACK(List.of("背包", "双肩包", "运动包"), List.of("背包")),
        CLOTHING(List.of("冲锋衣", "外套", "防晒衣", "衣服"), List.of("冲锋衣", "外套")),
        HEADPHONE(List.of("耳机", "降噪", "入耳", "头戴", "真无线"), List.of("耳机"));

        private final List<String> queryKeywords;
        private final List<String> productMarkers;

        ProductType(List<String> queryKeywords, List<String> productMarkers) {
            this.queryKeywords = queryKeywords;
            this.productMarkers = productMarkers;
        }

        static ProductType infer(String query) {
            for (ProductType type : values()) {
                if (type.queryKeywords.stream().anyMatch(query::contains)) {
                    return type;
                }
            }
            return null;
        }

        boolean matches(ProductResponse product) {
            String productText = String.join(" ",
                    product.name() == null ? "" : product.name(),
                    product.description() == null ? "" : product.description(),
                    product.tags() == null ? "" : String.join(" ", product.tags())
            );
            return productMarkers.stream().anyMatch(productText::contains);
        }
    }
}

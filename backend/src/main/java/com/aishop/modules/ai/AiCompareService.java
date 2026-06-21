package com.aishop.modules.ai;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.ai.AiCompareDimensionResult;
import com.aishop.infrastructure.ai.AiCompareItemResult;
import com.aishop.infrastructure.ai.AiCompareProductInput;
import com.aishop.infrastructure.ai.AiCompareResult;
import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.modules.ai.dto.CompareDimensionResponse;
import com.aishop.modules.ai.dto.CompareItemResponse;
import com.aishop.modules.ai.dto.CompareProductsRequest;
import com.aishop.modules.ai.dto.CompareProductsResponse;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.ProductResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiCompareService {
    private static final String DEFAULT_INTENT = "综合比较价格、口碑、热度和实用性";

    private final ProductService productService;
    private final AiServiceClient aiServiceClient;
    private final BehaviorService behaviorService;

    public AiCompareService(
            ProductService productService,
            AiServiceClient aiServiceClient,
            BehaviorService behaviorService
    ) {
        this.productService = productService;
        this.aiServiceClient = aiServiceClient;
        this.behaviorService = behaviorService;
    }

    public CompareProductsResponse compare(CurrentUser currentUser, CompareProductsRequest request) {
        if (request.productIds() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择2到4件商品");
        }
        List<String> productIds = request.productIds().stream().distinct().toList();
        if (productIds.size() < 2 || productIds.size() > 4) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择2到4件不同的商品");
        }

        List<ProductResponse> products = productIds.stream()
                .map(productService::getProduct)
                .toList();
        if (products.stream().anyMatch(product -> !"ON_SALE".equals(product.status()))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "对比清单中包含已下架商品");
        }

        String intent = normalizeIntent(request.intent());
        List<AiCompareProductInput> inputs = products.stream().map(this::toAiInput).toList();
        AiCompareResult aiResult = aiServiceClient.compareProducts(currentUser.userId(), intent, inputs);

        CompareProductsResponse response = isUsable(aiResult, productIds)
                ? fromAi(intent, products, aiResult)
                : buildRuleBased(intent, products);

        behaviorService.recordForUser(currentUser.userId(), new BehaviorEventRequest(
                "AI_COMPARE",
                null,
                null,
                Map.of(
                        "product_ids", productIds,
                        "intent", intent,
                        "source", response.source()
                )
        ));
        return response;
    }

    private CompareProductsResponse fromAi(
            String intent,
            List<ProductResponse> products,
            AiCompareResult result
    ) {
        Map<String, AiCompareItemResult> aiItems = safeList(result.items()).stream()
                .filter(item -> item != null && item.productId() != null)
                .collect(Collectors.toMap(
                        AiCompareItemResult::productId,
                        Function.identity(),
                        (left, right) -> left
                ));

        List<CompareItemResponse> items = products.stream().map(product -> {
            AiCompareItemResult item = aiItems.get(product.productId());
            if (item == null) {
                return fallbackItem(product, 70);
            }
            return new CompareItemResponse(
                    product.productId(),
                    clamp(item.score(), 0, 100, 70),
                    clean(item.verdict(), product.name() + "整体表现均衡"),
                    cleanList(item.strengths(), List.of("商品信息完整")),
                    cleanList(item.weaknesses(), List.of("建议结合实际需求判断"))
            );
        }).toList();

        List<CompareDimensionResponse> dimensions = safeList(result.dimensions()).stream()
                .filter(dimension -> dimension != null && dimension.name() != null)
                .map(dimension -> new CompareDimensionResponse(
                        clean(dimension.name(), "综合表现"),
                        normalizeScores(dimension.scores(), products)
                ))
                .limit(6)
                .toList();

        return new CompareProductsResponse(
                "AI",
                intent,
                result.winnerProductId(),
                clean(result.summary(), "已根据你的需求完成商品对比。"),
                cleanList(result.highlights(), List.of("已综合比较价格、评分、销量与商品特点")),
                items,
                dimensions.isEmpty() ? ruleDimensions(products, intent) : dimensions
        );
    }

    private CompareProductsResponse buildRuleBased(String intent, List<ProductResponse> products) {
        List<CompareDimensionResponse> dimensions = ruleDimensions(products, intent);
        Map<String, Integer> overallScores = new LinkedHashMap<>();
        for (ProductResponse product : products) {
            int score = (int) Math.round(dimensions.stream()
                    .mapToInt(dimension -> dimension.scores().getOrDefault(product.productId(), 0))
                    .average()
                    .orElse(0));
            overallScores.put(product.productId(), score);
        }

        ProductResponse winner = products.stream()
                .max((left, right) -> Integer.compare(
                        overallScores.get(left.productId()),
                        overallScores.get(right.productId())
                ))
                .orElse(products.get(0));

        List<CompareItemResponse> items = products.stream()
                .map(product -> fallbackItem(product, overallScores.get(product.productId())))
                .toList();
        List<String> highlights = List.of(
                winner.name() + "的综合得分最高（" + overallScores.get(winner.productId()) + "分）",
                "价格从 ¥" + minPrice(products) + " 到 ¥" + maxPrice(products),
                "结论已综合评分、销量、库存、价格及需求关键词"
        );

        return new CompareProductsResponse(
                "RULE_BASED",
                intent,
                winner.productId(),
                "当前 AI 模型不可用，系统使用商品真实数据完成对比。" + winner.name()
                        + "在本次需求下综合表现更均衡。",
                highlights,
                items,
                dimensions
        );
    }

    private List<CompareDimensionResponse> ruleDimensions(List<ProductResponse> products, String intent) {
        BigDecimal minPrice = products.stream().map(product -> new BigDecimal(product.price()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = products.stream().map(product -> new BigDecimal(product.price()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int maxSales = products.stream().mapToInt(ProductResponse::sales).max().orElse(1);

        Map<String, Integer> price = new LinkedHashMap<>();
        Map<String, Integer> rating = new LinkedHashMap<>();
        Map<String, Integer> popularity = new LinkedHashMap<>();
        Map<String, Integer> availability = new LinkedHashMap<>();
        Map<String, Integer> relevance = new LinkedHashMap<>();

        for (ProductResponse product : products) {
            price.put(product.productId(), priceScore(new BigDecimal(product.price()), minPrice, maxPrice));
            rating.put(product.productId(), clamp((int) Math.round(product.rating() * 20), 0, 100, 0));
            popularity.put(product.productId(), maxSales == 0 ? 60
                    : clamp((int) Math.round(product.sales() * 100.0 / maxSales), 0, 100, 0));
            availability.put(product.productId(), product.stock() > 0 ? Math.min(100, 65 + product.stock()) : 0);
            relevance.put(product.productId(), relevanceScore(product, intent));
        }

        return List.of(
                new CompareDimensionResponse("价格优势", price),
                new CompareDimensionResponse("用户口碑", rating),
                new CompareDimensionResponse("市场热度", popularity),
                new CompareDimensionResponse("库存保障", availability),
                new CompareDimensionResponse("需求匹配", relevance)
        );
    }

    private CompareItemResponse fallbackItem(ProductResponse product, int score) {
        List<String> strengths = new ArrayList<>();
        strengths.add("用户评分 " + product.rating() + " / 5.0");
        if (product.stock() > 0) {
            strengths.add("当前有货，共 " + product.stock() + " 件");
        }
        if (product.tags() != null && !product.tags().isEmpty()) {
            strengths.add("特点：" + String.join("、", product.tags().stream().limit(3).toList()));
        }

        List<String> weaknesses = new ArrayList<>();
        if (product.stock() <= 0) {
            weaknesses.add("当前缺货");
        }
        if (product.rating() < 4.0) {
            weaknesses.add("用户评分相对一般");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("仍需结合具体使用场景判断");
        }

        return new CompareItemResponse(
                product.productId(),
                clamp(score, 0, 100, 70),
                product.name() + "综合得分 " + clamp(score, 0, 100, 70) + " 分",
                strengths,
                weaknesses
        );
    }

    private int relevanceScore(ProductResponse product, String intent) {
        String normalizedIntent = intent.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        if (product.tags() != null) {
            tokens.addAll(product.tags());
        }
        tokens.add(product.name());
        tokens.add(product.categoryName());

        long matches = tokens.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> normalizedIntent.contains(value.toLowerCase(Locale.ROOT)))
                .count();
        return (int) Math.min(100, 60 + matches * 12);
    }

    private int priceScore(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) == 0) {
            return 85;
        }
        BigDecimal ratio = value.subtract(min)
                .divide(max.subtract(min), 4, RoundingMode.HALF_UP);
        return clamp(100 - ratio.multiply(BigDecimal.valueOf(40)).intValue(), 0, 100, 80);
    }

    private boolean isUsable(AiCompareResult result, List<String> productIds) {
        return result != null
                && result.winnerProductId() != null
                && productIds.contains(result.winnerProductId())
                && result.summary() != null
                && !result.summary().isBlank();
    }

    private Map<String, Integer> normalizeScores(
            Map<String, Integer> rawScores,
            List<ProductResponse> products
    ) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (ProductResponse product : products) {
            Integer value = rawScores == null ? null : rawScores.get(product.productId());
            scores.put(product.productId(), clamp(value, 0, 100, 60));
        }
        return scores;
    }

    private AiCompareProductInput toAiInput(ProductResponse product) {
        return new AiCompareProductInput(
                product.productId(),
                product.name(),
                product.description(),
                product.categoryName(),
                product.price(),
                product.stock(),
                product.sales(),
                product.rating(),
                product.tags() == null ? List.of() : product.tags()
        );
    }

    private String normalizeIntent(String intent) {
        return intent == null || intent.isBlank() ? DEFAULT_INTENT : intent.trim();
    }

    private String minPrice(List<ProductResponse> products) {
        return products.stream().map(product -> new BigDecimal(product.price()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).toPlainString();
    }

    private String maxPrice(List<ProductResponse> products) {
        return products.stream().map(product -> new BigDecimal(product.price()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).toPlainString();
    }

    private String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("<[^>]*>", "").trim();
    }

    private List<String> cleanList(List<String> values, List<String> fallback) {
        if (values == null) {
            return fallback;
        }
        List<String> cleaned = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> clean(value, ""))
                .filter(value -> !value.isBlank())
                .limit(6)
                .toList();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}

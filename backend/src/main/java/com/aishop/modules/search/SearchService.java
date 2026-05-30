package com.aishop.modules.search;

import com.aishop.modules.product.ProductService;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SearchService {
    private final ProductService productService;

    public SearchService(ProductService productService) {
        this.productService = productService;
    }

    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        return new SemanticSearchResponse(
                request.query(),
                false,
                productService.sampleSummaries(0.93, "语义匹配你的搜索意图")
        );
    }

    public ImageSearchResponse imageSearch(MultipartFile image, Integer limit) {
        return new ImageSearchResponse(
                "耳机",
                productService.sampleSummaries(0.88, "外观与上传图片相似")
        );
    }
}


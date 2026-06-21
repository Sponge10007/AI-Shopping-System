package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.persistence.repository.ProductImageRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.modules.internal.AiIndexNotifier;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.CreateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private AiIndexNotifier aiIndexNotifier;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productImageRepository, aiIndexNotifier);
    }

    @Test
    void createProductIndexesSearchableProductDescription() {
        CreateProductRequest request = new CreateProductRequest(
                "蓝牙降噪耳机",
                "适合通勤和办公，支持主动降噪",
                "c_headphone",
                new BigDecimal("299.00"),
                20,
                List.of("蓝牙", "长续航"),
                List.of()
        );

        productService.createProduct(new CurrentUser("m10001", "MERCHANT"), request);

        verify(aiIndexNotifier).notifyProductCreated(
                startsWith("p"),
                argThat(description ->
                        description.contains("蓝牙降噪耳机")
                                && description.contains("耳机")
                                && description.contains("适合通勤和办公")
                                && description.contains("蓝牙,长续航"))
        );
    }
}

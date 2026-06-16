package com.aishop;

import com.aishop.modules.admin.AdminController;
import com.aishop.modules.admin.AdminService;
import com.aishop.modules.ai.AiChatController;
import com.aishop.modules.ai.AiChatService;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import com.aishop.modules.auth.AuthController;
import com.aishop.modules.auth.AuthService;
import com.aishop.modules.behavior.BehaviorController;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.behavior.dto.BehaviorEventResponse;
import com.aishop.modules.internal.InternalProductController;
import com.aishop.modules.internal.InternalProductService;
import com.aishop.modules.internal.dto.BatchProductAiSummaryResponse;
import com.aishop.modules.internal.dto.ProductAiSummaryResponse;
import com.aishop.modules.order.OrderController;
import com.aishop.modules.order.OrderService;
import com.aishop.modules.product.ProductController;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.RestockResponse;
import com.aishop.modules.recommendation.RecommendationController;
import com.aishop.modules.recommendation.RecommendationService;
import com.aishop.modules.recommendation.dto.HomeRecommendationResponse;
import com.aishop.modules.search.SearchController;
import com.aishop.modules.search.SearchService;
import com.aishop.modules.search.dto.ImageSearchResponse;
import com.aishop.modules.search.dto.SemanticSearchRequest;
import com.aishop.modules.search.dto.SemanticSearchResponse;
import com.aishop.modules.upload.UploadController;
import com.aishop.modules.upload.UploadService;
import com.aishop.modules.upload.dto.UploadResponse;
import com.aishop.modules.user.UserController;
import com.aishop.modules.user.UserService;
import com.aishop.modules.user.dto.UserResponse;
import com.aishop.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        UserController.class,
        ProductController.class,
        SearchController.class,
        RecommendationController.class,
        BehaviorController.class,
        AiChatController.class,
        OrderController.class,
        AdminController.class,
        UploadController.class,
        InternalProductController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ApiControllerContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private UserService userService;
    @MockBean
    private ProductService productService;
    @MockBean
    private SearchService searchService;
    @MockBean
    private RecommendationService recommendationService;
    @MockBean
    private BehaviorService behaviorService;
    @MockBean
    private AiChatService aiChatService;
    @MockBean
    private OrderService orderService;
    @MockBean
    private AdminService adminService;
    @MockBean
    private UploadService uploadService;
    @MockBean
    private InternalProductService internalProductService;

    @BeforeEach
    void setUp() {
        when(authService.register(any())).thenReturn(TestFixtures.customerRegisterResponse());
        when(authService.login(any())).thenReturn(TestFixtures.loginResponse());
        when(authService.logout()).thenReturn(new com.aishop.modules.auth.dto.LogoutResponse(true));

        when(userService.getCurrentUser()).thenReturn(new UserResponse(
                "u10001",
                "alice",
                "13800000000",
                "CUSTOMER",
                "Alice",
                "https://example.com/avatar.png",
                OffsetDateTime.parse("2026-06-16T00:00:00Z")
        ));

        when(productService.listProducts(anyInt(), anyInt())).thenReturn(TestFixtures.productSummaryPage());
        when(productService.getProduct(anyString())).thenReturn(TestFixtures.product("10001"));
        when(productService.listMerchantProducts(nullable(String.class), anyInt(), anyInt()))
                .thenReturn(TestFixtures.productPage());
        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(new ProductMutationResponse("10099", "ON_SALE", "PENDING"));
        when(productService.restock(anyString(), any()))
                .thenReturn(new RestockResponse("10001", 130));

        when(searchService.semanticSearch(any(SemanticSearchRequest.class)))
                .thenReturn(new SemanticSearchResponse("headphones", false, List.of(TestFixtures.productSummary("10001"))));
        when(searchService.imageSearch(any(MultipartFile.class), anyInt()))
                .thenReturn(new ImageSearchResponse("headphones", List.of(TestFixtures.productSummary("10001"))));
        when(recommendationService.homeRecommendations(anyInt()))
                .thenReturn(new HomeRecommendationResponse("USER_PROFILE", List.of(TestFixtures.productSummary("10001"))));
        when(behaviorService.record(any(BehaviorEventRequest.class))).thenReturn(new BehaviorEventResponse(true));

        when(aiChatService.createSession(any(CreateChatSessionRequest.class)))
                .thenReturn(new ChatSessionResponse("s10001", "session", OffsetDateTime.parse("2026-06-16T00:00:00Z")));
        when(aiChatService.sendMessage(anyString(), any(ChatMessageRequest.class)))
                .thenReturn(new ChatMessageResponse("s10001", "answer", List.of(), List.of(), "raw", List.of()));
        when(aiChatService.clearHistory(anyString())).thenReturn(new ClearChatHistoryResponse(true));

        when(orderService.listOrders(nullable(String.class), anyInt(), anyInt())).thenReturn(TestFixtures.orderPage());
        when(orderService.getOrder(anyString())).thenReturn(TestFixtures.order("o10001", "CREATED"));
        when(adminService.listUsers(nullable(String.class), nullable(String.class), anyInt(), anyInt()))
                .thenReturn(TestFixtures.adminUserPage());
        when(adminService.overview()).thenReturn(TestFixtures.adminMetrics());
        when(uploadService.uploadProductImage(any(MultipartFile.class)))
                .thenReturn(new UploadResponse("img_1", "https://example.com/uploads/products/img_1.jpg", "PRODUCT_IMAGE"));
        when(uploadService.uploadSearchImage(any(MultipartFile.class)))
                .thenReturn(new UploadResponse("search_1", "https://example.com/uploads/search/search_1.jpg", "SEARCH_IMAGE"));
        when(internalProductService.getAiSummary(anyString()))
                .thenReturn(new ProductAiSummaryResponse("10001", "summary"));
        when(internalProductService.getAiSummaries(any()))
                .thenReturn(new BatchProductAiSummaryResponse(List.of(new ProductAiSummaryResponse("10001", "summary"))));
    }

    @Test
    void registerRequiresUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"phone":"13800000000","password":"Password123!","role":"CUSTOMER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message", containsString("username")));
    }

    @Test
    void loginReturnsUnifiedSuccessEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"account":"alice","password":"Password123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.access_token").value("dev-access-token"));
    }

    @Test
    void productListIsPublicAndPaged() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].product_id").value("10001"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void merchantCreateProductRejectsInvalidPrice() throws Exception {
        mockMvc.perform(post("/api/v1/merchant/products")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Invalid product",
                                  "description":"bad price",
                                  "price":0,
                                  "stock":10,
                                  "tags":["test"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message", containsString("price")));
    }

    @Test
    void merchantProductsCurrentlyDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].merchant_id").value("m10001"));
    }

    @Test
    void semanticSearchRejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/semantic")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"query":"","limit":20}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void behaviorEndpointCurrentlyAcceptsUnknownEventType() throws Exception {
        mockMvc.perform(post("/api/v1/behavior-events")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"event_type":"DROP_TABLE","metadata":{"source":"test"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true));
    }

    @Test
    void aiChatMessageRejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat/sessions/s10001/messages")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void orderCreateRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[],
                                  "receiver":{"name":"Alice","phone":"13800000000","address":"Hangzhou"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void adminUsersCurrentlyDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].role").value("CUSTOMER"));
    }

    @Test
    void internalProductSummaryCurrentlyDoesNotRequireInternalToken() throws Exception {
        mockMvc.perform(get("/internal/v1/products/10001/ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.product_id").value("10001"));
    }

    @Test
    void productImageUploadCurrentlyAcceptsNonImageMultipart() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "image",
                "payload.txt",
                "text/plain",
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/uploads/product-images").file(textFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.purpose").value("PRODUCT_IMAGE"));

        verify(uploadService).uploadProductImage(any(MultipartFile.class));
    }

}

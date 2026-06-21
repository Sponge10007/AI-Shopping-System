package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.common.security.jwt.JwtTokenProvider;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.admin.AdminController;
import com.aishop.modules.admin.AdminService;
import com.aishop.modules.ai.AiChatController;
import com.aishop.modules.ai.AiChatService;
import com.aishop.modules.ai.AiCompareController;
import com.aishop.modules.ai.AiCompareService;
import com.aishop.modules.ai.dto.ChatMessageRequest;
import com.aishop.modules.ai.dto.ChatMessageResponse;
import com.aishop.modules.ai.dto.ChatHistoryMessageResponse;
import com.aishop.modules.ai.dto.ChatSessionResponse;
import com.aishop.modules.ai.dto.ClearChatHistoryResponse;
import com.aishop.modules.ai.dto.CreateChatSessionRequest;
import com.aishop.modules.ai.dto.CompareProductsRequest;
import com.aishop.modules.ai.dto.CompareProductsResponse;
import com.aishop.modules.ai.dto.CompareItemResponse;
import com.aishop.modules.ai.dto.CompareDimensionResponse;
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
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.product.ProductController;
import com.aishop.modules.product.ProductService;
import com.aishop.modules.product.dto.CreateProductRequest;
import com.aishop.modules.product.dto.ProductMutationResponse;
import com.aishop.modules.product.dto.RestockRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import static org.mockito.ArgumentMatchers.argThat;
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
        AiCompareController.class,
        OrderController.class,
        AdminController.class,
        UploadController.class,
        InternalProductController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ApiControllerContractTest {
    private static final CurrentUser CUSTOMER = CurrentUser.prototypeCustomer();
    private static final CurrentUser MERCHANT = CurrentUser.prototypeMerchant();
    private static final CurrentUser ADMIN = new CurrentUser("a10001", "ADMIN");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserRepository userRepository;
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
    private AiCompareService aiCompareService;
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
        SecurityContextHolder.clearContext();

        when(authService.register(any())).thenReturn(TestFixtures.customerRegisterResponse());
        when(authService.login(any())).thenReturn(TestFixtures.loginResponse());
        when(authService.logout()).thenReturn(new com.aishop.modules.auth.dto.LogoutResponse(true));

        when(userService.getCurrentUser(any(CurrentUser.class))).thenReturn(new UserResponse(
                "u10001",
                "alice",
                "13800000000",
                "CUSTOMER",
                "Alice",
                "https://example.com/avatar.png",
                OffsetDateTime.parse("2026-06-16T00:00:00Z")
        ));

        when(productService.listProducts(
                anyInt(),
                anyInt(),
                nullable(String.class),
                nullable(String.class),
                nullable(String.class)
        )).thenReturn(TestFixtures.productSummaryPage());
        when(productService.getProduct(anyString())).thenReturn(TestFixtures.product("10001"));
        when(productService.listMerchantProducts(any(CurrentUser.class), nullable(String.class), anyInt(), anyInt()))
                .thenReturn(TestFixtures.productPage());
        when(productService.createProduct(any(CurrentUser.class), any(CreateProductRequest.class)))
                .thenReturn(new ProductMutationResponse("10099", "ON_SALE", "PENDING"));
        when(productService.restock(any(CurrentUser.class), anyString(), any(RestockRequest.class)))
                .thenReturn(new RestockResponse("10001", 130));

        when(searchService.semanticSearch(any(CurrentUser.class), any(SemanticSearchRequest.class)))
                .thenReturn(new SemanticSearchResponse("headphones", false, List.of(TestFixtures.productSummary("10001"))));
        when(searchService.imageSearch(any(CurrentUser.class), any(MultipartFile.class), anyInt()))
                .thenReturn(new ImageSearchResponse("headphones", List.of(TestFixtures.productSummary("10001"))));
        when(recommendationService.homeRecommendations(nullable(CurrentUser.class), anyInt()))
                .thenReturn(new HomeRecommendationResponse("USER_PROFILE", List.of(TestFixtures.productSummary("10001"))));
        when(behaviorService.recordEvent(anyString(), any(BehaviorEventRequest.class)))
                .thenReturn(new BehaviorEventResponse(true));

        when(aiChatService.createSession(any(CurrentUser.class), any(CreateChatSessionRequest.class)))
                .thenReturn(new ChatSessionResponse("s10001", "session", OffsetDateTime.parse("2026-06-16T00:00:00Z")));
        when(aiChatService.listSessions(any(CurrentUser.class)))
                .thenReturn(List.of(new ChatSessionResponse(
                        "s10001",
                        "通勤耳机",
                        OffsetDateTime.parse("2026-06-16T00:00:00Z")
                )));
        when(aiChatService.listMessages(any(CurrentUser.class), anyString()))
                .thenReturn(List.of(new ChatHistoryMessageResponse(
                        "user",
                        "推荐通勤耳机",
                        List.of(),
                        List.of(),
                        List.of(),
                        OffsetDateTime.parse("2026-06-16T00:01:00Z")
                )));
        when(aiChatService.sendMessage(any(CurrentUser.class), anyString(), any(ChatMessageRequest.class)))
                .thenReturn(new ChatMessageResponse("s10001", "answer", List.of(), List.of(), "raw", List.of()));
        when(aiChatService.clearHistory(any(CurrentUser.class), anyString()))
                .thenReturn(new ClearChatHistoryResponse(true));
        when(aiCompareService.compare(any(CurrentUser.class), any(CompareProductsRequest.class)))
                .thenReturn(new CompareProductsResponse(
                        "AI",
                        "通勤",
                        "10001",
                        "第一款更合适",
                        List.of("价格更低"),
                        List.of(new CompareItemResponse(
                                "10001", 92, "更均衡", List.of("便携"), List.of("无")
                        )),
                        List.of(new CompareDimensionResponse(
                                "价格优势", java.util.Map.of("10001", 95, "10002", 70)
                        ))
                ));

        when(orderService.createOrder(any(CurrentUser.class), any(CreateOrderRequest.class)))
                .thenReturn(TestFixtures.order("o10001", "CREATED"));
        when(orderService.listOrders(any(CurrentUser.class), nullable(String.class), anyInt(), anyInt()))
                .thenReturn(TestFixtures.orderPage());
        when(orderService.getOrder(any(CurrentUser.class), anyString()))
                .thenReturn(TestFixtures.order("o10001", "CREATED"));
        when(adminService.listUsers(nullable(String.class), nullable(String.class), anyInt(), anyInt()))
                .thenReturn(TestFixtures.adminUserPage());
        when(adminService.overview()).thenReturn(TestFixtures.adminMetrics());
        when(uploadService.uploadProductImage(any(MultipartFile.class)))
                .thenReturn(new UploadResponse("img_1", "https://example.com/uploads/products/img_1.jpg", "payload.txt", 12L));
        when(uploadService.uploadSearchImage(any(MultipartFile.class)))
                .thenReturn(new UploadResponse("search_1", "https://example.com/uploads/search/search_1.jpg", "payload.txt", 12L));
        when(internalProductService.getAiSummary(anyString()))
                .thenReturn(new ProductAiSummaryResponse("10001", "summary"));
        when(internalProductService.getAiSummaries(any()))
                .thenReturn(new BatchProductAiSummaryResponse(List.of(new ProductAiSummaryResponse("10001", "summary"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(CurrentUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
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
                .andExpect(jsonPath("$.data.access_token").value("dev-access-token"))
                .andExpect(jsonPath("$.data.refresh_token").value("dev-refresh-token"))
                .andExpect(jsonPath("$.data.expires_in").value(7200))
                .andExpect(jsonPath("$.data.user.user_id").value("u10001"));
    }

    @Test
    void productListIsPublicAndPaged() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].product_id").value("10001"))
                .andExpect(jsonPath("$.data.items[0].image_url").isNotEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.has_next").value(false));
    }

    @Test
    void merchantCreateProductRejectsInvalidPrice() throws Exception {
        authenticateAs(MERCHANT);

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
    void merchantCreateProductAcceptsSnakeCaseFields() throws Exception {
        authenticateAs(MERCHANT);

        mockMvc.perform(post("/api/v1/merchant/products")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Valid product",
                                  "description":"created through snake_case contract",
                                  "category_id":"c_headphone",
                                  "price":299.00,
                                  "stock":10,
                                  "tags":["audio"],
                                  "image_urls":["https://example.com/product.jpg"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product_id").value("10099"))
                .andExpect(jsonPath("$.data.vector_index_status").value("PENDING"));

        verify(productService).createProduct(
                any(CurrentUser.class),
                argThat(request ->
                        "c_headphone".equals(request.categoryId())
                                && request.imageUrls().equals(List.of("https://example.com/product.jpg")))
        );
    }

    @Test
    void merchantProductsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void merchantProductsReturnForMerchant() throws Exception {
        authenticateAs(MERCHANT);

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
        authenticateAs(CUSTOMER);

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
    void aiChatListsSavedSessionsAndMessages() throws Exception {
        authenticateAs(CUSTOMER);

        mockMvc.perform(get("/api/v1/ai/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].session_id").value("s10001"))
                .andExpect(jsonPath("$.data[0].title").value("通勤耳机"));

        mockMvc.perform(get("/api/v1/ai/chat/sessions/s10001/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("推荐通勤耳机"));
    }

    @Test
    void aiCompareAcceptsTwoToFourProductsAndReturnsStructuredReport() throws Exception {
        authenticateAs(CUSTOMER);

        mockMvc.perform(post("/api/v1/ai/compare")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "product_ids":["10001","10002"],
                                  "intent":"通勤"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.winner_product_id").value("10001"))
                .andExpect(jsonPath("$.data.dimensions[0].scores.10001").value(95));
    }

    @Test
    void orderCreateAcceptsSnakeCaseProductId() throws Exception {
        authenticateAs(CUSTOMER);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"product_id":"10001","quantity":2}],
                                  "receiver":{"name":"Alice","phone":"13800000000","address":"Hangzhou"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_id").value("o10001"))
                .andExpect(jsonPath("$.data.items[0].product_id").value("10001"))
                .andExpect(jsonPath("$.data.total_amount").value("598.00"));

        verify(orderService).createOrder(
                any(CurrentUser.class),
                argThat(request ->
                        request.items().size() == 1
                                && "10001".equals(request.items().get(0).productId()))
        );
    }

    @Test
    void orderCreateRejectsEmptyItems() throws Exception {
        authenticateAs(CUSTOMER);

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
    void adminUsersRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminUsersReturnForAdmin() throws Exception {
        authenticateAs(ADMIN);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].role").value("CUSTOMER"));
    }

    @Test
    void internalProductSummaryRequiresInternalToken() throws Exception {
        mockMvc.perform(get("/internal/v1/products/10001/ai-summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalProductSummaryReturnsWithInternalToken() throws Exception {
        mockMvc.perform(get("/internal/v1/products/10001/ai-summary")
                        .header("X-Internal-Token", "dev-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.product_id").value("10001"));
    }

    @Test
    void productImageUploadPassesAuthenticatedMerchantFileToService() throws Exception {
        authenticateAs(MERCHANT);

        MockMultipartFile textFile = new MockMultipartFile(
                "image",
                "payload.txt",
                "text/plain",
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/uploads/product-images").file(textFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.filename").value("payload.txt"))
                .andExpect(jsonPath("$.data.size").value(12));

        verify(uploadService).uploadProductImage(any(MultipartFile.class));
    }

}

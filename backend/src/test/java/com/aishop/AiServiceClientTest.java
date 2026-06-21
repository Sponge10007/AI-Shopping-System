package com.aishop;

import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.infrastructure.ai.AiCompareProductInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.ArrayList;

class AiServiceClientTest {

    private MockRestServiceServer server;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://127.0.0.1:9000")
                .defaultHeader("X-Internal-Token", "dev-internal-token");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build());
    }

    @Test
    void productIndexUsesPythonServiceContract() {
        server.expect(requestTo("http://127.0.0.1:9000/internal/v1/ai/products/p10001/index"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andExpect(content().json("""
                        {"description":"蓝牙降噪耳机，适合通勤"}
                        """))
                .andRespond(withSuccess("""
                        {"ok":true,"data":{"message":"商品向量写入任务已提交"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.indexProduct("p10001", "蓝牙降噪耳机，适合通勤")).isTrue();
        server.verify();
    }

    @Test
    void productIndexDeletionUsesDeleteRoute() {
        server.expect(requestTo("http://127.0.0.1:9000/internal/v1/ai/products/p10001/index"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andRespond(withSuccess("""
                        {"ok":true,"data":{"message":"商品向量已删除"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.deleteProductIndex("p10001")).isTrue();
        server.verify();
    }

    @Test
    void imageSearchSendsActualImageDataUrl() {
        server.expect(requestTo("http://127.0.0.1:9000/internal/v1/ai/search/image"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andExpect(content().json("""
                        {
                          "user_id":"u10001",
                          "image_path_or_url":"data:image/png;base64,iVBORw0KGgo=",
                          "distance_threshold":0.9,
                          "limit":5
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "ok":true,
                          "data":{
                            "keywords":["耳机"],
                            "query":"耳机",
                            "product_ids":["p10001"]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.imageSearch(
                "u10001",
                "data:image/png;base64,iVBORw0KGgo=",
                5,
                0.9
        );

        assertThat(response.productIds()).containsExactly("p10001");
        server.verify();
    }

    @Test
    void streamingChatForwardsDeltasAndReturnsFinalResult() {
        server.expect(requestTo("http://127.0.0.1:9000/internal/v1/ai/chat/messages/stream"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andExpect(content().json("""
                        {
                          "user_id":"u10001",
                          "session_id":"s10001",
                          "content":"推荐耳机"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"type":"delta","content":"推荐"}
                        {"type":"delta","content":"耳机"}
                        {"type":"done","data":{"answer":"推荐耳机","image_list":[],"link_list":[],"raw_answer":"推荐耳机"}}
                        """, MediaType.parseMediaType("application/x-ndjson")));

        List<String> deltas = new ArrayList<>();
        var result = client.streamChat("u10001", "s10001", "推荐耳机", deltas::add);

        assertThat(deltas).containsExactly("推荐", "耳机");
        assertThat(result.answer()).isEqualTo("推荐耳机");
        server.verify();
    }

    @Test
    void compareProductsUsesStructuredAiContract() {
        server.expect(requestTo("http://127.0.0.1:9000/internal/v1/ai/compare/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "dev-internal-token"))
                .andExpect(content().json("""
                        {
                          "user_id":"u10001",
                          "userId":"u10001",
                          "intent":"通勤",
                          "products":[{
                            "product_id":"10001",
                            "name":"耳机",
                            "description":"适合通勤",
                            "category":"耳机",
                            "price":"299.00",
                            "stock":10,
                            "sales":20,
                            "rating":4.8,
                            "tags":["降噪"]
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "ok":true,
                          "data":{
                            "winner_product_id":"10001",
                            "summary":"更适合通勤",
                            "highlights":["价格合理"],
                            "items":[],
                            "dimensions":[]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.compareProducts(
                "u10001",
                "通勤",
                List.of(new AiCompareProductInput(
                        "10001", "耳机", "适合通勤", "耳机",
                        "299.00", 10, 20, 4.8, List.of("降噪")
                ))
        );

        assertThat(result.winnerProductId()).isEqualTo("10001");
        server.verify();
    }
}

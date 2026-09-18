package com.relivus.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.RelivusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OpenAI 兼容 HTTP 客户端单元测试：正常解析 / markdown 包裹 / 4xx 不重试 / 5xx 有限重试 / 无法解析。
 *
 * <p>顺序要点：先构造客户端（其构造设置 SimpleClientHttpRequestFactory），再 {@code bindTo(builder)}
 * 把同一 builder 的 requestFactory 替换为 mock 工厂；客户端在请求时才 {@code builder.build()}，
 * 因此能取到 mock 工厂被拦截。
 */
class RestClientAiHttpClientTest {

    private static final String URL = "https://api.example.com/v1/chat/completions";

    private final ObjectMapper mapper = new ObjectMapper();
    private RelivusProperties props;

    @BeforeEach
    void setUp() {
        props = new RelivusProperties();
        props.getAi().setMaxRetries(2);
    }

    private static AiProviderConfig cfg() {
        return new AiProviderConfig("cfg1", "https://api.example.com/v1", "secret", "test-model");
    }

    private String response(String content) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("choices", List.of(Map.of("message", Map.of("content", content))));
        return mapper.writeValueAsString(root);
    }

    @Test
    void parsesJsonArrayOn2xx() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        RestClientAiHttpClient client = new RestClientAiHttpClient(builder, props);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(
                withSuccess(response("[\"a\",\"b\",\"c\"]"), MediaType.APPLICATION_JSON));

        List<String> result = client.completeBatch(cfg(), "生成名字", 3);
        assertThat(result).containsExactly("a", "b", "c");
        server.verify();
    }

    @Test
    void parsesMarkdownWrappedJsonArray() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        RestClientAiHttpClient client = new RestClientAiHttpClient(builder, props);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String content = "```json\n[\"alpha\",\"beta\",\"gamma\"]\n```";
        server.expect(requestTo(URL)).andRespond(withSuccess(response(content), MediaType.APPLICATION_JSON));

        List<String> result = client.completeBatch(cfg(), "p", 3);
        assertThat(result).containsExactly("alpha", "beta", "gamma");
        server.verify();
    }

    @Test
    void http4xxFailsImmediatelyWithoutRetry() {
        RestClient.Builder builder = RestClient.builder();
        RestClientAiHttpClient client = new RestClientAiHttpClient(builder, props);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"bad request\"}"));

        assertThatThrownBy(() -> client.completeBatch(cfg(), "p", 3))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_UPSTREAM_FAILED);
        // 仅一条期望：出现第二次请求会导致 verify 失败
        server.verify();
    }

    @Test
    void http5xxRetriesMaxRetriesThenFails() {
        RestClient.Builder builder = RestClient.builder();
        RestClientAiHttpClient client = new RestClientAiHttpClient(builder, props);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // maxRetries=2 → 共 3 次请求
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> client.completeBatch(cfg(), "p", 3))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_UPSTREAM_FAILED)
                .hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void unparseableContentThrowsServiceError() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        RestClientAiHttpClient client = new RestClientAiHttpClient(builder, props);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(response(""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.completeBatch(cfg(), "p", 3))
                .isInstanceOf(RelivusException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SERVICE_ERROR);
        server.verify();
    }
}
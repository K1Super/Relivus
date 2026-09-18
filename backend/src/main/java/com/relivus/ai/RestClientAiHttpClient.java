package com.relivus.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relivus.common.exception.ErrorCode;
import com.relivus.common.exception.RelivusException;
import com.relivus.config.RelivusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 HTTP 客户端（X-DOC：HTTP 同步调用、超时、有限重试、宽容解析）。
 *
 * <p>采用同步 {@link RestClient}（pom 仅有 spring-web，无 webflux）。请求体用 Jackson
 * {@link ObjectMapper} 序列化（禁止手拼 JSON，避免 prompt 内引号/换行破坏注入）；响应
 * {@code choices[0].message.content} 是字符串，按「JSON 数组 → 按行剥壳」两段宽容解析。
 *
 * <p>超时通过 {@link SimpleClientHttpRequestFactory} 的 Duration 版 setter 配置（Spring 6.1+，
 * 替代已废弃的 int 版 setConnectTimeout/setReadTimeout），并保留该 {@code ClientHttpRequestFactory}
 * 以便 {@code MockRestServiceServer.bindTo(RestClient.Builder)} 注入 mock 工厂做单元测试。
 */
@Component
public class RestClientAiHttpClient implements AiHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(RestClientAiHttpClient.class);

    private static final String GENERATOR_SYSTEM =
            "你是测试数据生成器。只输出一个JSON数组，数组元素个数与要求一致，每项为一个生成值，不要输出任何解释或markdown。";
    private static final String PING_SYSTEM = "你是连通性测试助手，请简短回复。";
    private static final int MAX_TOKENS = 4000;
    private static final int PING_MAX_TOKENS = 16;
    private static final int ERROR_BODY_TRUNCATE = 200;
    private static final int PING_SUMMARY_LENGTH = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient.Builder restClientBuilder;
    private final int maxRetries;

    public RestClientAiHttpClient(RestClient.Builder restClientBuilder, RelivusProperties props) {
        RelivusProperties.Ai ai = props.getAi();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(ai.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(ai.getReadTimeoutMs()));
        this.restClientBuilder = restClientBuilder.requestFactory(requestFactory);
        this.maxRetries = ai.getMaxRetries();
    }

    @Override
    public List<String> completeBatch(AiProviderConfig cfg, String prompt, int count) {
        if (count <= 0) {
            return List.of();
        }
        String user = prompt + "；需要" + count + "个值，以JSON数组输出";
        String content = chat(cfg, GENERATOR_SYSTEM, user, MAX_TOKENS);
        return parseValues(content, count);
    }

    @Override
    public String ping(AiProviderConfig cfg) {
        String content = chat(cfg, PING_SYSTEM, "ping", PING_MAX_TOKENS);
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.length() <= PING_SUMMARY_LENGTH
                ? content : content.substring(0, PING_SUMMARY_LENGTH);
    }

    private String chat(AiProviderConfig cfg, String system, String user, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", 0.9);
        body.put("max_tokens", maxTokens);
        String responseBody = post(cfg, body);
        return extractContent(responseBody);
    }

    /** POST 上游，5xx / IO / 超时 有限重试，4xx 不重试；业务失败统一抛 AI_UPSTREAM_FAILED。 */
    private String post(AiProviderConfig cfg, Map<String, Object> body) {
        String url = trimTrailingSlash(cfg.baseUrl()) + "/chat/completions";
        String payload = writeJson(body);
        int maxAttempts = maxRetries + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                RestClient client = restClientBuilder.build();
                ResponseEntity<String> response = client.post()
                        .uri(URI.create(url))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.apiKey())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(payload)
                        .retrieve()
                        .toEntity(String.class);
                return response.getBody();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status >= 500 && attempt < maxAttempts) {
                    sleepBackoff(attempt);
                    continue;
                }
                LOG.warn("AI 上游非 2xx：model={}, http={}", cfg.model(), status);
                throw new RelivusException(ErrorCode.AI_UPSTREAM_FAILED,
                        "AI 上游返回 HTTP " + status + "：" + truncate(e.getResponseBodyAsString(), ERROR_BODY_TRUNCATE));
            } catch (ResourceAccessException e) {
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                    continue;
                }
                LOG.warn("AI 上游连接失败/超时：model={}", cfg.model(), e);
                throw new RelivusException(ErrorCode.AI_UPSTREAM_FAILED, "AI 上游 TIMEOUT/连接失败", e);
            }
        }
    }

    /** 重试退避：500ms 起，逐次递增（500 * attempt）。 */
    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RelivusException(ErrorCode.AI_UPSTREAM_FAILED, "AI 请求被中断");
        }
    }

    /** 从 OpenAI 兼容响应中提取 choices[0].message.content。 */
    private String extractContent(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
            Object choices = root.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) {
                throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 返回格式无法解析");
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 返回格式无法解析");
            }
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> msg)) {
                throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 返回格式无法解析");
            }
            Object content = msg.get("content");
            return content == null ? null : String.valueOf(content);
        } catch (RelivusException e) {
            throw e;
        } catch (Exception e) {
            throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 返回格式无法解析", e);
        }
    }

    /** 宽容解析 content：优先取首个 '[' 到末个 ']' 之间子串解析 JSON 数组，失败按行剥壳。 */
    private List<String> parseValues(String content, int count) {
        List<String> values = tryParseJsonArray(content);
        if (values == null) {
            values = parseByLines(content);
        }
        if (values.isEmpty()) {
            throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 返回格式无法解析");
        }
        if (values.size() == count) {
            return values;
        }
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(values.get(Math.min(i, values.size() - 1)));
        }
        return result;
    }

    private List<String> tryParseJsonArray(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            List<?> parsed = objectMapper.readValue(trimmed.substring(start, end + 1), new TypeReference<>() {
            });
            List<String> out = new ArrayList<>(parsed.size());
            for (Object o : parsed) {
                out.add(o == null ? "" : String.valueOf(o));
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseByLines(String content) {
        List<String> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        for (String raw : content.split("\\R")) {
            String line = stripDecoration(raw);
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    /** 去除 markdown 围栏、编号前缀、首尾引号/反引号。 */
    private String stripDecoration(String raw) {
        String s = raw.trim();
        s = s.replaceAll("^[-*•]\\s*", "");
        s = s.replaceAll("^\\d+[.)、]\\s*", "");
        s = s.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                    || (first == '`' && last == '`')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RelivusException(ErrorCode.AI_SERVICE_ERROR, "AI 请求体序列化失败", e);
        }
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String truncate(String body, int maxLength) {
        if (body == null) {
            return "";
        }
        return body.length() <= maxLength ? body : body.substring(0, maxLength);
    }
}
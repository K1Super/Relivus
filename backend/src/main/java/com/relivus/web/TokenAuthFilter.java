package com.relivus.web;

import com.relivus.config.RelivusProperties;
import com.relivus.common.api.ApiResponse;
import com.relivus.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一 Bearer Token 鉴权过滤器。
 *
 * <p>所有 {@code /api/**} 请求必须携带 {@code Authorization: Bearer ${RELIVUS_TOKEN}}，Token
 * 来自环境变量 {@code RELIVUS_TOKEN}。校验失败返回 HTTP 401 + 错误码 100003。
 *
 * <p>放行路径：健康检查、OpenAPI 文档与 API 入口之外的静态资源。Token 校验采用常量时间比较，
 * 不做日志明文回显。
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthFilter.class);

    static final String AUTH_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";
    static final String API_PREFIX = "/api/";
    static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public TokenAuthFilter(RelivusProperties props, ObjectMapper objectMapper) {
        this.expectedToken = props.getSecurity().getToken();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader(AUTH_HEADER);
        if (auth == null || !auth.startsWith(BEARER_PREFIX) || !constantTimeEquals(expectedToken, auth.substring(BEARER_PREFIX.length()))) {
            log.warn("Authentication failed, path={}, ip={}", request.getRequestURI(), request.getRemoteAddr());
            ApiResponse<Void> body = ApiResponse.error(ErrorCode.AUTH_FAILED, "Unauthorized");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(CONTENT_TYPE_JSON);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 常量时间比较 Token，避免时序侧信道。 */
    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }
}
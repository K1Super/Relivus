package com.relivus.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 TraceId 过滤器。
 *
 * <p>优先沿用请求头 {@code X-Trace-Id}（前端会话级全局 TraceId），缺失时后端生成并回写响应头，
 * 保证端到端链路贯通。TraceId 写入 MDC，异步链路经 {@code TaskDecorator} 透传；请求结束清理 MDC，
 * 防止线程复用残留。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            traceId = newTraceId();
        }
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
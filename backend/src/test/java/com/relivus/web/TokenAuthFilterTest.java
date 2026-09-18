package com.relivus.web;

import com.relivus.common.api.ApiResponse;
import com.relivus.config.RelivusProperties;
import com.relivus.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bearer Token 鉴权过滤器测试（DOC-08：无 Token / 错 Token 抛 9001）。
 */
class TokenAuthFilterTest {

    private static final String VALID_TOKEN = "relivus-secret-token";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RelivusProperties props = new RelivusProperties();
        props.getSecurity().setToken(VALID_TOKEN);
        this.mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new TokenAuthFilter(props, new com.fasterxml.jackson.databind.ObjectMapper()))
                .build();
    }

    @RestController
    private static class ProbeController {
        @GetMapping("/api/probe")
        public ApiResponse<String> probe() {
            return ApiResponse.ok("ok");
        }

        @GetMapping("/probe")
        public ApiResponse<String> publicProbe() {
            return ApiResponse.ok("ok");
        }
    }

    @Test
    void missingHeader_rejectsWith401And9001() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FAILED.getCode()));
    }

    @Test
    void wrongToken_rejectsWith401And9001() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_FAILED.getCode()));
    }

    @Test
    void nonBearerHeader_rejects() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_passesThrough() throws Exception {
        mockMvc.perform(get("/api/probe").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void nonApiPath_isNotFiltered() throws Exception {
        mockMvc.perform(get("/probe").header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void constantTimeEquals_basicBehavior() {
        assertThat(TokenAuthFilter.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(TokenAuthFilter.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(TokenAuthFilter.constantTimeEquals("abc", "abcd")).isFalse();
        assertThat(TokenAuthFilter.constantTimeEquals(null, "abc")).isFalse();
        assertThat(TokenAuthFilter.constantTimeEquals("abc", null)).isFalse();
        assertThat(TokenAuthFilter.constantTimeEquals(null, null)).isFalse();
        assertThat(TokenAuthFilter.constantTimeEquals("", "")).isTrue();
    }
}
package com.relivus.controller;

import com.relivus.common.api.ApiResponse;
import com.relivus.dto.AiConfigResponse;
import com.relivus.dto.AiTestResponse;
import com.relivus.dto.CreateAiConfigRequest;
import com.relivus.service.IAiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型配置管理接口。
 */
@Tag(name = "AI 模型配置")
@RestController
@RequestMapping("/api/ai/configs")
public class AiConfigController {

    private final IAiConfigService aiConfigService;

    public AiConfigController(IAiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @Operation(summary = "AI 配置列表")
    @GetMapping
    public ApiResponse<List<AiConfigResponse>> list() {
        return ApiResponse.ok(aiConfigService.list());
    }

    @Operation(summary = "创建 AI 配置")
    @PostMapping
    public ApiResponse<AiConfigResponse> create(@Valid @RequestBody CreateAiConfigRequest request) {
        return ApiResponse.ok(aiConfigService.create(request));
    }

    @Operation(summary = "更新 AI 配置")
    @PutMapping("/{id}")
    public ApiResponse<AiConfigResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody CreateAiConfigRequest request) {
        return ApiResponse.ok(aiConfigService.update(id, request));
    }

    @Operation(summary = "删除 AI 配置")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiConfigService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "激活 AI 配置")
    @PostMapping("/{id}/activate")
    public ApiResponse<AiConfigResponse> activate(@PathVariable Long id) {
        return ApiResponse.ok(aiConfigService.activate(id));
    }

    @Operation(summary = "测试 AI 配置连通性")
    @PostMapping("/{id}/test")
    public ApiResponse<AiTestResponse> test(@PathVariable Long id) {
        return ApiResponse.ok(aiConfigService.test(id));
    }
}
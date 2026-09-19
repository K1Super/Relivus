package com.relivus.controller;

import com.relivus.common.api.ApiResponse;
import com.relivus.dto.ConnectionResponse;
import com.relivus.dto.ConnectionTestResult;
import com.relivus.dto.CreateConnectionRequest;
import com.relivus.service.ConnectionService;
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
 * 目标库连接管理接口。
 */
@Tag(name = "连接管理")
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Operation(summary = "连接列表")
    @GetMapping
    public ApiResponse<List<ConnectionResponse>> list() {
        return ApiResponse.ok(connectionService.list());
    }

    @Operation(summary = "创建连接")
    @PostMapping
    public ApiResponse<ConnectionResponse> create(@Valid @RequestBody CreateConnectionRequest request) {
        return ApiResponse.ok(connectionService.create(request));
    }

    @Operation(summary = "更新连接")
    @PutMapping("/{id}")
    public ApiResponse<ConnectionResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody CreateConnectionRequest request) {
        return ApiResponse.ok(connectionService.update(id, request));
    }

    @Operation(summary = "删除连接")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "测试已保存连接")
    @PostMapping("/{id}/test")
    public ApiResponse<ConnectionTestResult> test(@PathVariable Long id) {
        return ApiResponse.ok(connectionService.testById(id));
    }
}
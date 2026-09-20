package com.relivus.service;

import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.ConnectionResponse;
import com.relivus.dto.ConnectionTestResult;
import com.relivus.dto.CreateConnectionRequest;
import com.relivus.entity.ConnectionEntity;

import javax.sql.DataSource;
import java.util.List;

/**
 * 目标库连接业务接口。
 *
 * <p>负责连接的增删改查、连通性测试、连接密码加解密，以及按连接 ID 解析/缓存
 * 目标库 {@link DataSource} 与 {@link DatabaseDialect}。连接密码以 AES-GCM 密文存储，
 * 任何出参均不得携带明文密码。
 */
public interface IConnectionService {

    /**
     * 查询全部连接（按创建时间倒序）。
     *
     * @return 连接列表，密码字段不回传
     */
    List<ConnectionResponse> list();

    /**
     * 新建连接：先做连通性测试，通过后加密存储密码。
     *
     * @param request 连接创建请求
     * @return 新建连接的出参
     */
    ConnectionResponse create(CreateConnectionRequest request);

    /**
     * 更新连接；请求携带密码时重新加密，未携带密码时保留原密文。
     *
     * @param id      连接 ID
     * @param request 连接更新请求
     * @return 更新后的连接出参
     */
    ConnectionResponse update(Long id, CreateConnectionRequest request);

    /**
     * 删除连接，同时注销其缓存的数据源。
     *
     * @param id 连接 ID
     */
    void delete(Long id);

    /**
     * 对请求体中的连接信息做一次性连通性测试，不访问元库、不落库。
     *
     * @param request 连接信息
     * @return 连通性测试结果
     */
    ConnectionTestResult test(CreateConnectionRequest request);

    /**
     * 对已保存的连接做连通性测试。
     *
     * @param id 连接 ID
     * @return 连通性测试结果
     */
    ConnectionTestResult testById(Long id);

    /**
     * 解析连接对应的目标库数据源（带缓存）。
     *
     * @param connectionId 连接 ID
     * @return 目标库数据源
     */
    DataSource resolveDataSource(Long connectionId);

    /**
     * 解析连接对应的数据库方言。
     *
     * @param connectionId 连接 ID
     * @return 数据库方言
     */
    DatabaseDialect dialect(Long connectionId);

    /**
     * 按 ID 获取连接实体，不存在时抛出 NOT_FOUND 业务异常。
     *
     * @param id 连接 ID
     * @return 连接实体（含密文密码，仅限服务端内部使用，禁止直接出参）
     */
    ConnectionEntity requireEntity(Long id);
}

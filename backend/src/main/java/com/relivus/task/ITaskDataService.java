package com.relivus.task;

import com.relivus.dialect.DatabaseDialect;
import com.relivus.dto.GenerationConfig;
import com.relivus.dto.TaskDataPage;
import com.relivus.dto.TaskGeneratedTable;
import com.relivus.entity.TaskEntity;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 任务数据回看业务接口。
 *
 * <p>负责生成前各表行数基线采集、任务涉及表清单解析，以及生成数据的在线分页回看。
 * 回看读取目标库真实数据，分页采用方言 keyset/分页语法，单次读取行数受限。
 */
public interface ITaskDataService {

    /** 在线回看单次读取的最大行数。 */
    int MAX_LIMIT = 200;

    /**
     * 生成执行前采集各目标表的回看基线。
     *
     * <p>truncate 模式统一为 0（回看全表）；追加模式取数值主键的 {@code MAX(主键)}；
     * 无数值主键的表为 {@code null}（回看时按全表展示）。
     *
     * @param dataSource 目标库数据源
     * @param dialect    目标库方言
     * @param config     生成配置
     * @return 表名 → 主键基线值（可为 null）
     */
    Map<String, Long> captureBaselines(DataSource dataSource, DatabaseDialect dialect,
                                       GenerationConfig config);

    /**
     * 解析任务涉及的表清单（来自任务配置），并标记每张表是否可按主键基线精确筛选本次生成数据。
     *
     * @param task 任务实体
     * @return 任务涉及表清单
     */
    List<TaskGeneratedTable> listTables(TaskEntity task);

    /**
     * 分页读取任务生成的数据。
     *
     * @param task   任务实体
     * @param table  表名
     * @param limit  本次读取行数（上限 {@link #MAX_LIMIT}）
     * @param offset 偏移量
     * @return 数据分页结果（列、行、分页信息）
     */
    TaskDataPage readData(TaskEntity task, String table, int limit, int offset);
}

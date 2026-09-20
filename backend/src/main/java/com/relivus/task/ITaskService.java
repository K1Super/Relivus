package com.relivus.task;

import com.relivus.entity.TaskEntity;

import java.util.List;

/**
 * 统一任务业务接口。
 *
 * <p>负责生成/脱敏任务的创建、异步执行调度、状态机流转
 * （PENDING → RUNNING → SUCCESS / FAILED / CANCELLED）、取消与查询。
 * 取消采用「内存标志 + 数据库 cancel_requested」双标志，保证重启后仍可取消。
 */
public interface ITaskService {

    /**
     * 创建任务并落库为 PENDING。
     *
     * @param taskType     任务类型（GENERATION / MASKING）
     * @param connectionId 目标库连接 ID
     * @param configJson   任务配置 JSON
     * @return 新任务 ID
     */
    Long createTask(String taskType, Long connectionId, String configJson);

    /**
     * 提交任务到线程池异步执行；线程池满时任务置 FAILED 并抛出拒绝异常。
     *
     * @param taskId 任务 ID
     * @param job    任务执行体（生成或脱敏）
     */
    void executeAsync(Long taskId, TaskJob job);

    /**
     * 请求取消任务：置内存标志与数据库标志，运行中的任务在下个检查点终止为 CANCELLED；
     * 仍在排队（PENDING）的任务直接置 CANCELLED 终态。
     *
     * @param taskId 任务 ID
     * @return 取消请求受理成功返回 true
     * @throws com.relivus.common.exception.RelivusException 任务已处于终态时抛出 TASK_CANCEL_FAILED
     */
    boolean cancel(Long taskId);

    /**
     * 按 ID 获取任务，不存在时抛出 NOT_FOUND 业务异常。
     *
     * @param taskId 任务 ID
     * @return 任务实体
     */
    TaskEntity getTask(Long taskId);

    /**
     * 回写生成数据基线 JSON（引擎真正插入目标库之前采集后立即写入，用于生成数据在线回看）。
     *
     * @param taskId           任务 ID
     * @param dataBaselineJson 数据基线 JSON
     */
    void updateDataBaseline(Long taskId, String dataBaselineJson);

    /**
     * 分页查询任务列表（按创建时间倒序）。
     *
     * @param limit  最多返回条数
     * @param offset 偏移量
     * @return 任务列表
     */
    List<TaskEntity> listTasks(int limit, int offset);
}

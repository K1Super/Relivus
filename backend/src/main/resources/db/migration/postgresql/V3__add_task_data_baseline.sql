-- ============================================================
-- Relivus 元库迁移（PostgreSQL 方言）V3：生成数据可视化基线
-- 说明：data_baseline_json 记录生成任务开始前各表 MAX(主键) 基线，
--       JSON 形如 {"users": 42, "orders": 0}；任务成功后前端按
--       「主键 > 基线」精确回看本次生成的行（truncate 模式基线为 0）。
-- ============================================================

ALTER TABLE df_task ADD COLUMN data_baseline_json TEXT NULL;

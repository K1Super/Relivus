-- ============================================================
-- Relivus 演示目标库结构升级脚本（MySQL 版）
-- 用途：将既有 relivus_demo 升级到新结构（gender 男/女、balance 18,2 非负、NOT NULL、注释）。
-- 执行：mysql -h 127.0.0.1 -u root -p relivus_demo < upgrade_cleanup.sql
-- 注意：升级前建议先清空数据（DELETE FROM orders; DELETE FROM users;），
--       避免 ENUM 变更时旧字母值（M/F/O）被置空串；随后用修复后的引擎重新生成。
-- ============================================================

-- 1. gender 枚举改为 男/女 + 注释
ALTER TABLE users
    MODIFY gender ENUM ('男', '女') NOT NULL COMMENT '性别：男/女';

-- 2. balance 加宽为 DECIMAL(18,2) + 非负 CHECK
ALTER TABLE users
    MODIFY balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '账户余额，保留 2 位小数，不允许为负';

ALTER TABLE users
    ADD CONSTRAINT users_balance_check CHECK (balance >= 0);

-- 3. 非空补齐 + 列注释（与 init.sql 对齐）
ALTER TABLE users
    MODIFY age INT NOT NULL COMMENT '年龄，约束 18~70',
    MODIFY email VARCHAR(255) NOT NULL COMMENT '登录邮箱，唯一，RFC 合规 ASCII 地址',
    MODIFY name VARCHAR(64) NOT NULL COMMENT '姓名（现代常用中文名，2~4 字）',
    MODIFY created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（不超过当前时间）';

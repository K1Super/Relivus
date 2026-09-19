-- ============================================================
-- Relivus 初始化脚本（MySQL 版）— 以 root 执行一次
-- 执行：mysql -h 127.0.0.1 -u root -p < init.sql
-- 说明：
--   1. 创建应用用户 relivus 与元库 relivus_meta；元库表结构由应用启动时
--      Flyway 自动迁移（db/migration/mysql/V1__init.sql），此处不建表。
--   2. 创建演示目标库 relivus_demo（users/orders）；目标库由用户在 UI 中
--      配置连接，Relivus 不迁移目标库。
--   3. 密码为占位值，部署前务必修改为强密码并同步到 /etc/relivus/relivus.env。
-- ============================================================

CREATE DATABASE IF NOT EXISTS relivus_meta DEFAULT CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'relivus'@'%' IDENTIFIED BY 'change-me-strong';
GRANT ALL PRIVILEGES ON relivus_meta.* TO 'relivus'@'%';
FLUSH PRIVILEGES;

CREATE DATABASE IF NOT EXISTS relivus_demo DEFAULT CHARACTER SET utf8mb4;
USE relivus_demo;

-- ---------- 演示目标库结构 ----------
CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL COMMENT '登录邮箱，唯一，纯 ASCII 码（不含汉字）',
    name       VARCHAR(64) NOT NULL COMMENT '姓名（现代常用中文名，2~4 字）',
    age        INT NOT NULL COMMENT '年龄，约束 18~70',
    gender     ENUM ('男', '女') NOT NULL COMMENT '性别：男/女',
    balance    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '账户余额，保留 2 位小数，不允许为负',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（不超过当前时间）',
    CONSTRAINT users_age_check CHECK (age >= 18 AND age <= 70),
    CONSTRAINT users_balance_check CHECK (balance >= 0),
    CONSTRAINT users_email_key UNIQUE (email),
    KEY idx_users_name (name)
) COMMENT='用户表';

CREATE TABLE orders (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(10,2),
    status     ENUM ('NEW', 'PAID', 'SHIPPED', 'DONE') DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT orders_amount_check CHECK (amount > 0),
    CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id)
);
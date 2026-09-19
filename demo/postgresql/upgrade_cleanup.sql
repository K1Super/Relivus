-- ============================================================
-- Relivus 演示目标库结构升级脚本（PostgreSQL 版）
-- 用途：将既有 relivus_demo 升级到新结构（gender 男/女、balance 18,2 非负、NOT NULL、注释）。
-- 执行：psql -h 127.0.0.1 -U postgres -d relivus_demo -f upgrade_cleanup.sql
-- 注意：升级后建议清空数据（如通过生成任务 truncateBefore=true）并用修复后的引擎重新生成，
--       旧数据中的中文邮箱/古风姓名/字母性别不会被保留。
-- ============================================================

-- 1. gender 枚举重建：M/F/O → 男/女（新建类型→USING 转换→删除旧类型→改名）
CREATE TYPE public.user_gender_new AS ENUM ('男', '女');

ALTER TABLE public.users
    ALTER COLUMN gender TYPE public.user_gender_new
        USING (CASE gender::text WHEN 'M' THEN '男' WHEN 'F' THEN '女' ELSE '男' END)::public.user_gender_new;

DROP TYPE public.user_gender;
ALTER TYPE public.user_gender_new RENAME TO user_gender;

COMMENT ON TYPE public.user_gender IS '用户性别：男/女';

-- 2. balance：加宽为 numeric(18,2)，并补非负 CHECK（幂等）
ALTER TABLE public.users
    ALTER COLUMN balance TYPE numeric(18,2);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'users_balance_check' AND conrelid = 'users'::regclass) THEN
        ALTER TABLE public.users ADD CONSTRAINT users_balance_check CHECK (balance >= 0);
    END IF;
END $$;

-- 3. 非空补齐（与 init.sql 对齐）
ALTER TABLE public.users
    ALTER COLUMN age SET NOT NULL,
    ALTER COLUMN gender SET NOT NULL,
    ALTER COLUMN balance SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

-- 4. 列注释
COMMENT ON COLUMN public.users.email IS '登录邮箱，唯一，纯 ASCII 码（不含汉字）';
COMMENT ON COLUMN public.users.name IS '姓名（现代常用中文名，2~4 字）';
COMMENT ON COLUMN public.users.age IS '年龄，约束 18~70';
COMMENT ON COLUMN public.users.gender IS '性别：男/女';
COMMENT ON COLUMN public.users.balance IS '账户余额，保留 2 位小数，不允许为负';
COMMENT ON COLUMN public.users.created_at IS '创建时间（不超过当前时间）';

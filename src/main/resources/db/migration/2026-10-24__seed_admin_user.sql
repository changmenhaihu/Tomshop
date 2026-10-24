-- ============================================================================
-- 增量迁移：初始化后台管理员账号
--
-- 日期：2026-10-24
-- 影响范围：仅向 sys_user / sys_user_role 各插入一行（已存在则跳过）。
--           不修改、不删除任何既有数据。
--
-- 背景：阶段 1 建库时刻意没有初始化 sys_user（当时还没有登录体系，密码无法安全落库）。
--       阶段 3 的 Sa-Token 登录必须有一个可用的账号来验证「登录 -> 取角色 -> 取权限
--       -> 取动态路由」整条链路，因此在当前库里补一条管理员账号。
--
-- 账号：admin / admin123（BCrypt cost=10）
--       **这是本地开发凭据，上线前必须改密**。改密只需执行：
--         UPDATE sys_user SET password = '<新的 BCrypt 密文>' WHERE username = 'admin';
--       生成密文可调用 POST /api/v1/auth/register 注册一个临时账号后，
--       从其库内 password 字段拷贝，再用它覆盖 admin 的密码。
--
-- 为什么 SQL 里可以出现 BCrypt 密文：
--     BCrypt 是单向加盐哈希，同一明文每次生成的结果都不同。这里留的是
--     "admin123" 的哈希，而不是明文 —— 即便脚本进版本库，也不会泄露任何人的真实密码。
--
-- 执行方式：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 tomshop \
--         < src/main/resources/db/migration/2026-10-24__seed_admin_user.sql
--
-- 幂等性：
--   两条 INSERT 都带 NOT EXISTS 守卫，重复执行不会报错、不会产生重复行。
--
-- 回滚：
--   DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username = 'admin');
--   DELETE FROM sys_user      WHERE username = 'admin';
--   （执行前请确认该账号下没有已关联的业务数据，如购物车、订单，否则会留下孤儿行）
-- ============================================================================

USE `tomshop`;

-- ----------------------------------------------------------------------------
-- 第 0 步：前置校验 —— 目标库的表结构必须是阶段 3 之后的形态
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_guard_seed_admin`;
DELIMITER $$
CREATE PROCEDURE `sp_guard_seed_admin`()
BEGIN
    DECLARE v_user_table   INT DEFAULT 0;
    DECLARE v_rel_table    INT DEFAULT 0;
    DECLARE v_admin_role   INT DEFAULT 0;

    SELECT COUNT(*) INTO v_user_table
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_user';

    SELECT COUNT(*) INTO v_rel_table
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_role';

    IF v_user_table = 0 OR v_rel_table = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '中止：sys_user 或 sys_user_role 不存在，请先执行 schema.sql';
    END IF;

    -- 角色必须先存在，否则后面的关联插入会静默跳过（WHERE 不成立），
    -- 留下一个「能登录但没有任何菜单」的账号，排查起来很费劲。
    SELECT COUNT(*) INTO v_admin_role
    FROM sys_role
    WHERE id = 1 AND role_code = 'ADMIN' AND deleted = 0;

    IF v_admin_role = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '中止：未找到 role_code=ADMIN 的角色';
    END IF;
END$$
DELIMITER ;

CALL `sp_guard_seed_admin`();
DROP PROCEDURE `sp_guard_seed_admin`;

-- ----------------------------------------------------------------------------
-- 第 1 步：插入管理员账号（固定 id=1001，与初始化数据的小整数主键风格一致）
--
-- 密码为 "admin123" 的 BCrypt 密文（$2a$，cost=10）。
-- 若已存在同名账号则整条 SELECT 不返回行，INSERT 什么也不做。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `gender`, `status`, `deleted`, `version`)
SELECT 1001,
       'admin',
       '$2a$10$1Gwy8bd8T612rAVnePIURu.W/MRleKhzR7MtZ.KRb/kcRjdAq4r3q',
       '超级管理员',
       0,
       1,
       0,
       0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_user` WHERE `username` = 'admin'
);

-- ----------------------------------------------------------------------------
-- 第 2 步：绑定 ADMIN 角色
--
-- 关联行的 user_id 从 sys_user 里按用户名反查，而不是硬编码 1001 ——
-- 如果库里本来就有一个手工创建的 admin（id 不是 1001），第 1 步会跳过插入，
-- 本步仍能把角色绑到那个真实存在的账号上。
--
-- 关联 id 用 1001 之外的固定值，避免与雪花 ID 或将来手工插入的行冲突。
-- sys_user_role 是纯关系表（无逻辑删除列），重复关联由 uk_user_role 唯一键兜底，
-- 这里再用 NOT EXISTS 拦一次，保证脚本可重复执行。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`)
SELECT 1002, u.id, r.id
FROM `sys_user` u
         INNER JOIN `sys_role` r ON r.id = 1 AND r.role_code = 'ADMIN' AND r.deleted = 0
WHERE u.username = 'admin'
  AND u.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

-- ----------------------------------------------------------------------------
-- 第 3 步：结果核对（人工检查输出，不影响脚本成败）
-- ----------------------------------------------------------------------------
SELECT u.id,
       u.username,
       u.nickname,
       u.status,
       r.role_code,
       (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.role_id = r.id)      AS menu_count,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS perm_count
FROM `sys_user` u
         LEFT JOIN `sys_user_role` ur ON ur.user_id = u.id
         LEFT JOIN `sys_role` r ON r.id = ur.role_id
WHERE u.username = 'admin';

-- ============================================================================
-- 增量迁移：sys_role_permission.permission_id 更名为 perm_id
--
-- 日期：2026-10-15
-- 影响范围：**仅 sys_role_permission 一张表的列名与索引名**，纯元数据操作，
--           不搬运、不删除任何数据行（18 行授权数据原样保留）。
--
-- 目的：让两张关联表的命名风格一致 ——
--   sys_role_permission(role_id, perm_id)   指向 sys_permission
--   sys_role_menu      (role_id, menu_id)   指向 sys_menu
-- 关联表统一用「role_id + 语义化的被授权对象 id」，读 SQL 时不必再回头查
-- sys_role_permission.permission_id 到底指的是权限还是菜单。
--
-- 注意：**主表 sys_permission 的主键列仍为 id**，继续沿用 BaseEntity 的统一约定
-- （所有业务实体主键都叫 id）。本次只改关联表侧的列名，因此 JOIN 写法为：
--   ON srp.perm_id = p.id
--
-- 执行方式：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 tomshop \
--         < src/main/resources/db/migration/2026-10-15__rename_permission_id_to_perm_id.sql
--
-- 为什么本次不做整表备份：
--   这是纯列名变更（ALTER TABLE ... CHANGE COLUMN / RENAME INDEX），数据行不移动，
--   失败时回滚 DDL 即可，不存在「改到一半数据丢了」的窗口。
--   另外上一个迁移留下的 bak_20261013_sys_role_permission 已经是一份完整的数据快照，
--   真要恢复数据也有依据，不必再堆一张备份表。
-- ============================================================================

USE `tomshop`;

-- ----------------------------------------------------------------------------
-- 第 0 步：前置校验
--
-- 两条都必须成立，否则说明脚本已执行过或表结构与预期不符 —— 主动中止，
-- 避免在错误的库/错误的表上做了一次静默的无效变更。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_guard_rename_perm_id`;
DELIMITER $$
CREATE PROCEDURE `sp_guard_rename_perm_id`()
BEGIN
    DECLARE v_old_col   INT DEFAULT 0;
    DECLARE v_new_col   INT DEFAULT 0;
    DECLARE v_old_uk    INT DEFAULT 0;
    DECLARE v_row_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_old_col FROM information_schema.`COLUMNS`
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
       AND COLUMN_NAME = 'permission_id';

    SELECT COUNT(*) INTO v_new_col FROM information_schema.`COLUMNS`
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
       AND COLUMN_NAME = 'perm_id';

    SELECT COUNT(*) INTO v_old_uk FROM information_schema.`STATISTICS`
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
       AND INDEX_NAME = 'uk_role_permission';

    IF v_old_col <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '前置校验失败：sys_role_permission.permission_id 不存在，可能已执行过本迁移，或表结构与预期不符。';
    END IF;

    IF v_new_col <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '前置校验失败：sys_role_permission.perm_id 已存在，本迁移可能已执行过。';
    END IF;

    IF v_old_uk = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '前置校验失败：唯一键 uk_role_permission 不存在，表结构与预期不符。';
    END IF;

    SELECT COUNT(*) INTO v_row_count FROM `sys_role_permission`;
    SELECT CONCAT('迁移前 sys_role_permission 行数：', v_row_count) AS `前置状态`;
END$$
DELIMITER ;

CALL `sp_guard_rename_perm_id`();
DROP PROCEDURE `sp_guard_rename_perm_id`;

-- ----------------------------------------------------------------------------
-- 第 1 步：列更名
--
-- CHANGE COLUMN 必须重复完整列定义，此处与原定义 (BIGINT NOT NULL COMMENT '权限ID') 一致，
-- 不借机改类型或可空性 —— 一次迁移只做一件事，出问题时归因才清晰。
-- ----------------------------------------------------------------------------
ALTER TABLE `sys_role_permission`
    CHANGE COLUMN `permission_id` `perm_id` BIGINT NOT NULL COMMENT '权限ID（sys_permission.id）';

-- ----------------------------------------------------------------------------
-- 第 2 步：索引更名（索引名同样是对外可见的标识，跟着列名走）
-- ----------------------------------------------------------------------------
ALTER TABLE `sys_role_permission`
    RENAME INDEX `uk_role_permission` TO `uk_role_perm`;

ALTER TABLE `sys_role_permission`
    RENAME INDEX `idx_permission_id` TO `idx_perm_id`;

-- ----------------------------------------------------------------------------
-- 第 3 步：迁移结果自检
-- ----------------------------------------------------------------------------
SELECT 'sys_role_permission' AS `表`, COUNT(*) AS `行数` FROM `sys_role_permission`
UNION ALL SELECT 'sys_permission',        COUNT(*) FROM `sys_permission`
UNION ALL SELECT 'sys_role_menu',         COUNT(*) FROM `sys_role_menu`
UNION ALL SELECT 'product_category',      COUNT(*) FROM `product_category`;   -- 必须仍为迁移前的行数

SELECT `COLUMN_NAME` AS `列名`, `COLUMN_TYPE` AS `类型`, `IS_NULLABLE` AS `可空`, `COLUMN_COMMENT` AS `注释`
  FROM information_schema.`COLUMNS`
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
 ORDER BY `ORDINAL_POSITION`;

SELECT `INDEX_NAME` AS `索引名`, GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) AS `列`
  FROM information_schema.`STATISTICS`
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission'
 GROUP BY `INDEX_NAME`;

-- ============================================================================
-- 回滚方式：
--
--   USE tomshop;
--   ALTER TABLE `sys_role_permission`
--       RENAME INDEX `uk_role_perm` TO `uk_role_permission`;
--   ALTER TABLE `sys_role_permission`
--       RENAME INDEX `idx_perm_id` TO `idx_permission_id`;
--   ALTER TABLE `sys_role_permission`
--       CHANGE COLUMN `perm_id` `permission_id` BIGINT NOT NULL COMMENT '权限ID';
-- ============================================================================

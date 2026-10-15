-- ============================================================================
-- 增量迁移：拆分 sys_permission —— 菜单内容与权限内容解耦
--
-- 日期：2026-10-13
-- 影响范围：**仅鉴权域的三张表**（sys_permission 结构变更，sys_role_permission 数据重刷，
--           新增 sys_menu / sys_role_menu）。
--           商品域 / 购物车域 / 订单域**一行不动** —— 库中已存在人工通过接口创建的
--           真实分类数据（如「蓝牙耳机」），整库重建会丢失。
--
-- 迁移内容：
--   拆分前：sys_permission 一张表同时承担「导航树」与「权限点」，用 type 1/2/3 区分
--           目录/菜单/按钮，parent_id 自关联成树，perms 挂在菜单行上。
--   拆分后：
--     sys_menu       导航结构（目录/菜单，树形，parent_id 自关联），由 sys_role_menu 授权
--     sys_permission 权限点（扁平，无 parent_id），由 sys_role_permission 授权
--     sys_role_menu  新增的关联表
--   两套体系**无结构性关联**：新增接口权限不必动菜单；接口型权限可以完全不挂菜单。
--
-- 执行方式：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 tomshop \
--         < src/main/resources/db/migration/2026-10-13__split_menu_permission.sql
--
-- 回滚：迁移过程中会把原表整表备份为 bak_20261013_sys_permission /
--       bak_20261013_sys_role_permission。回滚见文件末尾注释。
-- ============================================================================

USE `tomshop`;

-- ----------------------------------------------------------------------------
-- 第 0 步：前置校验
--
-- sys_permission 与 sys_role_permission 当前应当**只含 data.sql 的种子数据**。
-- 若行数不符，说明有人工新增的授权配置，脚本会主动中止 —— 宁可迁移失败让开发者
-- 人工核对，也不能静默丢弃真实的权限配置（这类丢失表现为「线上某些菜单突然消失」，
-- 排查成本极高）。
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_guard_menu_permission_split`;
DELIMITER $$
CREATE PROCEDURE `sp_guard_menu_permission_split`()
BEGIN
    DECLARE v_perm_rows INT DEFAULT 0;
    DECLARE v_rp_rows   INT DEFAULT 0;

    SELECT COUNT(*) INTO v_perm_rows FROM `sys_permission`;
    SELECT COUNT(*) INTO v_rp_rows   FROM `sys_role_permission`;

    IF v_perm_rows <> 11 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '前置校验失败：sys_permission 行数不是预期的 11，疑似存在人工新增数据，请先人工核对后再迁移。';
    END IF;

    IF v_rp_rows <> 11 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            '前置校验失败：sys_role_permission 行数不是预期的 11，疑似存在人工新增的授权，请先人工核对后再迁移。';
    END IF;
END$$
DELIMITER ;

CALL `sp_guard_menu_permission_split`();
DROP PROCEDURE `sp_guard_menu_permission_split`;

-- ----------------------------------------------------------------------------
-- 第 1 步：整表备份（回滚依据）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `bak_20261013_sys_permission`;
CREATE TABLE `bak_20261013_sys_permission` AS SELECT * FROM `sys_permission`;

DROP TABLE IF EXISTS `bak_20261013_sys_role_permission`;
CREATE TABLE `bak_20261013_sys_role_permission` AS SELECT * FROM `sys_role_permission`;

-- ----------------------------------------------------------------------------
-- 第 2 步：重建 sys_permission 为「纯权限点」形态
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
    `id`          BIGINT       NOT NULL COMMENT '主键，雪花算法',
    `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `perms`       VARCHAR(128) NOT NULL COMMENT '权限标识，格式 module:resource:action，如 system:user:query',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1按钮 2接口',
    `module`      VARCHAR(64)  NULL     COMMENT '所属业务模块，仅用于后台分组展示，与菜单无结构性关联',
    `description` VARCHAR(256) NULL     COMMENT '权限说明',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perms` (`perms`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限表';

-- ----------------------------------------------------------------------------
-- 第 3 步：新增 sys_menu 与 sys_role_menu
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
    `id`          BIGINT       NOT NULL COMMENT '主键，雪花算法',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0 表示根菜单',
    `name`        VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `menu_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1目录 2菜单',
    `path`        VARCHAR(256) NULL     COMMENT '前端路由路径',
    `component`   VARCHAR(256) NULL     COMMENT '前端组件路径，目录类型为空',
    `icon`        VARCHAR(64)  NULL     COMMENT '图标',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '同级排序，值越小越靠前',
    `visible`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏：0显示 1隐藏',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单表';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
    `id`      BIGINT NOT NULL COMMENT '主键，雪花算法',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单关联表';

-- ----------------------------------------------------------------------------
-- 第 4 步：导入菜单种子数据
-- ----------------------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `menu_type`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
    (1,   0,  '系统管理', 1, '/system',           NULL,                     'setting',  1, 0, 1),
    (11,  1,  '用户管理', 2, '/system/user',      'system/user/index',      'user',     1, 0, 1),
    (12,  1,  '角色管理', 2, '/system/role',      'system/role/index',      'peoples',  2, 0, 1),
    (13,  1,  '菜单管理', 2, '/system/menu',      'system/menu/index',      'tree',     3, 0, 1),
    (14,  1,  '权限管理', 2, '/system/permission','system/permission/index','lock',     4, 0, 1),
    (2,   0,  '商品管理', 1, '/product',          NULL,                     'shopping', 2, 0, 1),
    (21,  2,  '商品列表', 2, '/product/spu',      'product/spu/index',      'list',     1, 0, 1),
    (22,  2,  '分类管理', 2, '/product/category', 'product/category/index', 'menu',     2, 0, 1),
    (3,   0,  '订单管理', 1, '/order',            NULL,                     'form',     3, 0, 1),
    (31,  3,  '订单列表', 2, '/order/list',       'order/list/index',       'documentation', 1, 0, 1);

-- ----------------------------------------------------------------------------
-- 第 5 步：导入权限点种子数据
--
-- 与旧数据的对应关系（人工核对用）：
--   system:user:list      -> 拆为 system:user:query（菜单权限消失，只留接口权限）
--   system:permission:list-> 拆为 system:permission:query
--   order:list            -> 规范化为 order:info:query
-- 旧 id 全部不复用，避免与备份表比对时产生歧义。
-- ----------------------------------------------------------------------------
INSERT INTO `sys_permission` (`id`, `name`, `perms`, `type`, `module`, `description`, `status`) VALUES
    (101, '用户查询',   'system:user:query',       1, '系统管理', '查看用户列表与详情',       1),
    (102, '用户新增',   'system:user:add',         1, '系统管理', '新增用户',                 1),
    (103, '用户编辑',   'system:user:edit',        1, '系统管理', '修改用户资料与状态',       1),
    (104, '用户删除',   'system:user:delete',      1, '系统管理', '删除用户',                 1),
    (105, '角色查询',   'system:role:query',       1, '系统管理', '查看角色列表与详情',       1),
    (106, '角色维护',   'system:role:save',        1, '系统管理', '新增/编辑角色并分配授权',  1),
    (107, '菜单查询',   'system:menu:query',       1, '系统管理', '查看菜单树',               1),
    (108, '菜单维护',   'system:menu:save',        1, '系统管理', '新增/编辑/删除菜单',       1),
    (109, '权限查询',   'system:permission:query', 1, '系统管理', '查看权限点列表',           1),
    (110, '权限维护',   'system:permission:save',  1, '系统管理', '新增/编辑/删除权限点',     1),
    (201, '商品查询',   'product:spu:query',       1, '商品管理', '查看商品列表与详情',       1),
    (202, '商品维护',   'product:spu:save',        1, '商品管理', '新增/编辑商品与 SKU',      1),
    (203, '商品上下架', 'product:spu:status',      1, '商品管理', '切换商品上架/下架状态',    1),
    (204, '分类查询',   'product:category:query',  1, '商品管理', '查看分类树与详情',         1),
    (205, '分类维护',   'product:category:save',   1, '商品管理', '新增/编辑/删除商品分类',   1),
    (301, '订单查询',   'order:info:query',        1, '订单管理', '查看订单列表与详情',       1),
    (302, '订单发货',   'order:info:deliver',      1, '订单管理', '对已支付订单执行发货',     1),
    (303, '订单取消',   'order:info:cancel',       1, '订单管理', '取消未发货订单',           1);

-- ----------------------------------------------------------------------------
-- 第 6 步：重刷角色授权
--
-- 旧 sys_role_permission 的 permission_id 指向的是「菜单/按钮混合」的旧 id，语义已失效，
-- 直接清空后按新权限点重建（前置校验已确认其只含种子数据）。
-- 自增/其他表不受影响。
-- ----------------------------------------------------------------------------
DELETE FROM `sys_role_permission`;

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`) VALUES
    (1,  1, 101), (2,  1, 102), (3,  1, 103), (4,  1, 104), (5,  1, 105),
    (6,  1, 106), (7,  1, 107), (8,  1, 108), (9,  1, 109), (10, 1, 110),
    (11, 1, 201), (12, 1, 202), (13, 1, 203), (14, 1, 204), (15, 1, 205),
    (16, 1, 301), (17, 1, 302), (18, 1, 303);

INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`) VALUES
    (1,  1, 1),  (2,  1, 11), (3,  1, 12), (4,  1, 13), (5,  1, 14),
    (6,  1, 2),  (7,  1, 21), (8,  1, 22),
    (9,  1, 3),  (10, 1, 31);

-- ----------------------------------------------------------------------------
-- 第 7 步：迁移结果自检
-- ----------------------------------------------------------------------------
SELECT 'sys_menu'           AS `表`, COUNT(*) AS `行数` FROM `sys_menu`
UNION ALL SELECT 'sys_permission',     COUNT(*) FROM `sys_permission`
UNION ALL SELECT 'sys_role_menu',      COUNT(*) FROM `sys_role_menu`
UNION ALL SELECT 'sys_role_permission',COUNT(*) FROM `sys_role_permission`
UNION ALL SELECT 'product_category',   COUNT(*) FROM `product_category`;   -- 必须仍为迁移前的行数

-- ============================================================================
-- 回滚方式（仅在需要撤销本次迁移时执行）：
--
--   USE tomshop;
--   DROP TABLE IF EXISTS `sys_menu`;
--   DROP TABLE IF EXISTS `sys_role_menu`;
--   DROP TABLE IF EXISTS `sys_permission`;
--   RENAME TABLE `bak_20261013_sys_permission` TO `sys_permission`;
--   DELETE FROM `sys_role_permission`;
--   INSERT INTO `sys_role_permission` SELECT * FROM `bak_20261013_sys_role_permission`;
--
-- 确认迁移无误、系统运行正常后，可清理备份表：
--   DROP TABLE `bak_20261013_sys_permission`;
--   DROP TABLE `bak_20261013_sys_role_permission`;
-- ============================================================================

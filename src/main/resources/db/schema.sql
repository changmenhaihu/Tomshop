-- ============================================================================
-- TomShop 数据库初始化脚本（schema.sql）
--
-- 目标环境：MySQL 8.0+，字符集 utf8mb4，collation utf8mb4_0900_ai_ci
-- 执行方式：mysql -h 127.0.0.1 -P 3306 -u root -p < schema.sql
--
-- 设计约定（务必在新增表时遵守）：
--   1. 主键一律 BIGINT，由应用侧雪花算法（MyBatis-Plus ASSIGN_ID）生成，
--      不使用 AUTO_INCREMENT —— 为后续分库分表铺路，且不暴露业务量级。
--   2. 所有「业务实体表」统一携带五个公共字段（与 BaseEntity 对齐）：
--        create_time DATETIME   创建时间（应用自动填充）
--        update_time DATETIME   更新时间（应用自动填充）
--        deleted     TINYINT    逻辑删除标记 0=未删 1=已删（应用 @TableLogic 维护）
--        version     INT        乐观锁版本号（应用 @Version 维护，用于并发扣库存等场景）
--   3. 关联表（sys_user_role / sys_role_permission / sys_role_menu）不设上述五个公共字段，
--      走物理删除 —— 关联关系没有「软删除」语义，删掉就是没授权。
--   4. 不使用物理外键（FOREIGN KEY）：高并发下物理外键引入额外锁与校验开销，
--      分库分表后也无法跨库。引用完整性由「索引 + 应用层事务」保证。
--      因此所有外键列都必须建普通索引，避免无索引 JOIN 退化为全表扫描。
--   5. 金额一律 DECIMAL(10,2)，Java 侧 BigDecimal，禁止用 FLOAT/DOUBLE 存钱。
--   6. 时间字段不设零日期默认值（本实例 sql_mode 含 NO_ZERO_DATE，会拒绝 '0000-00-00'）。
--      公共时间字段由应用填充，数据库 DEFAULT 仅作绕过应用的兜底。
--   7. 每张表的每个字段都写 COMMENT，作为元数据留档。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 建库
-- ----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `tomshop`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `tomshop`;

-- ============================================================================
-- 一、用户与权限域（阶段 3 接入 Shiro 后启用鉴权，此处先建表预埋）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 用户表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`              BIGINT       NOT NULL COMMENT '主键，雪花算法',
    `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(128) NOT NULL COMMENT '密码哈希（阶段3起为 BCrypt，禁止存明文）',
    `nickname`        VARCHAR(64)  NULL     COMMENT '昵称',
    `avatar`          VARCHAR(512) NULL     COMMENT '头像 URL',
    `phone`           VARCHAR(20)  NULL     COMMENT '手机号',
    `email`           VARCHAR(128) NULL     COMMENT '邮箱',
    `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1正常 0封禁',
    `last_login_time` DATETIME     NULL     COMMENT '最后登录时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------------------------------------------------------
-- 2. 角色表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id`          BIGINT      NOT NULL COMMENT '主键，雪花算法',
    `role_name`   VARCHAR(64) NOT NULL COMMENT '角色名称（展示用）',
    `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码（鉴权用，如 ADMIN）',
    `description` VARCHAR(256) NULL    COMMENT '角色描述',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ----------------------------------------------------------------------------
-- 3. 菜单表（导航结构：目录 / 菜单）
--
-- 与权限表的关系：**无结构性关联**，两者彼此独立，仅通过角色间接绑定。
--   sys_menu       回答「前端能看到哪些导航」    —— 由 sys_role_menu 授权
--   sys_permission 回答「后端允许执行什么操作」  —— 由 sys_role_permission 授权
-- 这样「只读管理员」可以只挂菜单、不挂写权限；接口型权限（type=2）也可以
-- 完全不出现在任何菜单下。
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

-- ----------------------------------------------------------------------------
-- 4. 权限表（权限点：按钮 / 接口，扁平结构，支撑「注解 + 动态权限」）
--
-- perms 唯一约束是刻意设计：重复的权限标识会导致「同一个标识在库里存了两行、
-- 只给角色挂了其中一行」这类难以排查的鉴权失效问题。
-- module 只用于后台管理界面的分组展示，是普通字符串标签，不含任何外键关联。
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
-- 5. 用户-角色关联表（物理删除）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id`      BIGINT NOT NULL COMMENT '主键，雪花算法',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联表';

-- ----------------------------------------------------------------------------
-- 6. 角色-权限关联表（物理删除）—— 授权「能做什么」
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
    `id`      BIGINT NOT NULL COMMENT '主键，雪花算法',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `perm_id` BIGINT NOT NULL COMMENT '权限ID（sys_permission.id）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `perm_id`),
    KEY `idx_perm_id` (`perm_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联表';

-- ----------------------------------------------------------------------------
-- 7. 角色-菜单关联表（物理删除）—— 授权「能看见什么」
--
-- 与 sys_role_permission 完全平行、互不推导：
-- 挂上了菜单不等于拥有该菜单下所有按钮/接口权限，反之亦然。
-- 前端据此渲染动态路由，后端 Shiro 依据 sys_role_permission 做接口拦截。
-- ----------------------------------------------------------------------------
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
-- 8. 收货地址表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
    `id`              BIGINT       NOT NULL COMMENT '主键，雪花算法',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户ID',
    `receiver_name`   VARCHAR(64)  NOT NULL COMMENT '收货人姓名',
    `receiver_phone`  VARCHAR(20)  NOT NULL COMMENT '收货人手机号',
    `province`        VARCHAR(64)  NOT NULL COMMENT '省',
    `city`            VARCHAR(64)  NOT NULL COMMENT '市',
    `district`        VARCHAR(64)  NOT NULL COMMENT '区/县',
    `detail_address`  VARCHAR(256) NOT NULL COMMENT '详细地址（街道门牌）',
    `is_default`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址：1是 0否',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址表';

-- ============================================================================
-- 二、商品域
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 9. 商品分类表（树形，parent_id 自关联）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category` (
    `id`          BIGINT      NOT NULL COMMENT '主键，雪花算法',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示根分类',
    `name`        VARCHAR(64) NOT NULL COMMENT '分类名称',
    `level`       TINYINT     NOT NULL DEFAULT 1 COMMENT '层级：1一级 2二级 3三级',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '同级排序，值越小越靠前',
    `icon`        VARCHAR(512) NULL    COMMENT '分类图标 URL',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类表';

-- ----------------------------------------------------------------------------
-- 10. 商品 SPU 表（标准化产品单元：决定「是什么」，不含规格差异）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_spu`;
CREATE TABLE `product_spu` (
    `id`           BIGINT        NOT NULL COMMENT '主键，雪花算法',
    `category_id`  BIGINT        NOT NULL COMMENT '所属分类ID（product_category.id）',
    `name`         VARCHAR(128)  NOT NULL COMMENT '商品名称',
    `subtitle`     VARCHAR(256)  NULL     COMMENT '副标题/卖点',
    `main_image`   VARCHAR(512)  NULL     COMMENT '主图 URL',
    `images`       VARCHAR(2048) NULL     COMMENT '轮播图，JSON 数组字符串',
    `detail`       TEXT          NULL     COMMENT '富文本详情（HTML）',
    `price`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '展示价（取 SKU 最低价）',
    `sales`        INT           NOT NULL DEFAULT 0 COMMENT '累计销量（冗余，用于排序）',
    `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品 SPU 表';

-- ----------------------------------------------------------------------------
-- 11. 商品 SKU 表（库存计价单元：决定「卖哪一款」，库存/价格在此维护）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
    `id`          BIGINT        NOT NULL COMMENT '主键，雪花算法',
    `spu_id`      BIGINT        NOT NULL COMMENT '所属 SPU ID（product_spu.id）',
    `sku_name`    VARCHAR(128)  NOT NULL COMMENT '规格名称，如「红色/XL」',
    `spec`        VARCHAR(512)  NULL     COMMENT '规格明细，JSON 对象字符串，如 {"颜色":"红","尺码":"XL"}',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '销售单价',
    `stock`       INT           NOT NULL DEFAULT 0 COMMENT '库存数量（并发扣减靠 version 乐观锁）',
    `sales`       INT           NOT NULL DEFAULT 0 COMMENT '该 SKU 累计销量',
    `image`       VARCHAR(512)  NULL     COMMENT 'SKU 图片 URL',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`spu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品 SKU 表';

-- ============================================================================
-- 三、购物车域
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 12. 购物车条目表（同一用户 + 同一 SKU 唯一，重复加购则累加数量）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `cart_item`;
CREATE TABLE `cart_item` (
    `id`          BIGINT   NOT NULL COMMENT '主键，雪花算法',
    `user_id`     BIGINT   NOT NULL COMMENT '所属用户ID',
    `sku_id`      BIGINT   NOT NULL COMMENT 'SKU ID（product_sku.id）',
    `quantity`    INT      NOT NULL DEFAULT 1 COMMENT '数量',
    `selected`    TINYINT  NOT NULL DEFAULT 1 COMMENT '是否选中：1选中 0未选中',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`     INT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车条目表';

-- ============================================================================
-- 四、订单域
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 13. 订单主表（收货信息与商品信息均为下单时的「快照」，避免源数据变更影响历史订单）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
    `id`              BIGINT        NOT NULL COMMENT '主键，雪花算法',
    `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单号（业务号，唯一，对客展示）',
    `user_id`         BIGINT        NOT NULL COMMENT '下单用户ID',
    `total_amount`    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    `freight_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `pay_amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额 = 总额 - 优惠 + 运费',
    `pay_type`        TINYINT       NULL     COMMENT '支付方式：1支付宝 2微信 3银行卡（预留）',
    `status`          TINYINT       NOT NULL DEFAULT 10 COMMENT '订单状态：10待支付 20已支付 30已发货 40已完成 50已取消 60已关闭',
    `receiver_name`   VARCHAR(64)   NULL     COMMENT '收货人姓名（快照）',
    `receiver_phone`  VARCHAR(20)   NULL     COMMENT '收货人手机号（快照）',
    `receiver_address` VARCHAR(512) NULL     COMMENT '收货地址（快照，省市区+详细地址拼接）',
    `remark`          VARCHAR(256)  NULL     COMMENT '买家备注',
    `pay_time`        DATETIME      NULL     COMMENT '支付时间',
    `delivery_time`   DATETIME      NULL     COMMENT '发货时间',
    `finish_time`     DATETIME      NULL     COMMENT '完成时间',
    `cancel_time`     DATETIME      NULL     COMMENT '取消时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

-- ----------------------------------------------------------------------------
-- 14. 订单明细表（商品信息为下单时的「快照」）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id`            BIGINT        NOT NULL COMMENT '主键，雪花算法',
    `order_id`      BIGINT        NOT NULL COMMENT '所属订单ID（order_info.id）',
    `order_no`      VARCHAR(64)   NOT NULL COMMENT '订单号（冗余，便于明细独立查询）',
    `spu_id`        BIGINT        NOT NULL COMMENT 'SPU ID（快照来源）',
    `sku_id`        BIGINT        NOT NULL COMMENT 'SKU ID（快照来源）',
    `spu_name`      VARCHAR(128)  NOT NULL COMMENT '商品名称（快照）',
    `sku_name`      VARCHAR(128)  NOT NULL COMMENT '规格名称（快照）',
    `sku_spec`      VARCHAR(512)  NULL     COMMENT '规格明细 JSON（快照）',
    `product_image` VARCHAR(512)  NULL     COMMENT '商品图片（快照）',
    `price`         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '成交单价（快照）',
    `quantity`      INT           NOT NULL DEFAULT 1 COMMENT '购买数量',
    `total_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '小计 = 单价 * 数量',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `version`       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表';

-- ============================================================================
-- 完成。共 14 张表：
--   用户与权限域（8）：sys_user / sys_role / sys_menu / sys_permission /
--                     sys_user_role / sys_role_permission / sys_role_menu / user_address
--   商品域（3）      ：product_category / product_spu / product_sku
--   购物车域（1）    ：cart_item
--   订单域（2）      ：order_info / order_item
--
-- 鉴权模型小结（阶段 3 接入 Shiro 时按此实现）：
--   用户 --sys_user_role--> 角色 --sys_role_permission--> 权限点  → 决定「能不能调这个接口」
--                           角色 --sys_role_menu-------> 菜单    → 决定「能不能看到这个入口」
-- 两条链路彼此独立，不互相推导。
-- ============================================================================

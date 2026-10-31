-- ============================================================================
-- 增量迁移：为高频查询补充复合索引（阶段 4 性能优化）
--
-- 日期：2026-10-24
-- 影响范围：只新增二级索引，不改表结构、不搬运任何数据行。
--           执行的 DDL 均为 InnoDB 在线建索引（8.0 默认 INPLACE + 允许并发 DML）。
--
-- 背景：阶段 2 的单列索引只能覆盖「等值过滤」，撑不住实际查询的
--       「多条件 + 排序」组合，表现为 EXPLAIN 里出现 Using filesort：
--         1) 商品列表：category_id IN (...) / status = ?  ORDER BY create_time DESC
--         2) 商品详情：spu_id = ?  ORDER BY price ASC
--         3) 我的订单：user_id = ? [AND status = ?]  ORDER BY create_time DESC
--       数据量小的时候 filesort 看不出来，量一上来就是「首页越刷越慢」。
--
-- 为什么单列索引不够：
--       MySQL 对每个查询最多只能高效利用一个索引的「前缀」。现有 idx_category_id
--       能定位到某分类下的行，但排序仍需回表后 filesort；复合索引把过滤列与排序列
--       放进同一棵树，排序直接由索引顺序满足。
--
-- 执行方式：
--   mysql -h 127.0.0.1 -P 3306 -u root -p --default-character-set=utf8mb4 tomshop \
--         < src/main/resources/db/migration/2026-10-24__add_query_indexes.sql
--
-- 幂等性：
--   每个索引先查 information_schema.statistics，已存在则跳过。
--   MySQL 8.0 的 CREATE INDEX 不支持 IF NOT EXISTS，因此用存储过程封装。
--
-- 回滚：
--   ALTER TABLE product_spu DROP INDEX idx_deleted_create_time;
--   ALTER TABLE product_spu DROP INDEX idx_status_create_time;
--   ALTER TABLE product_spu DROP INDEX idx_category_status_create;
--   ALTER TABLE product_sku DROP INDEX idx_spu_price;
--   ALTER TABLE order_info  DROP INDEX idx_user_status_create;
--   ALTER TABLE order_info  DROP INDEX idx_user_create;
--   （回滚只影响性能，不影响任何业务数据的完整性）
-- ============================================================================

USE `tomshop`;

-- ----------------------------------------------------------------------------
-- 第 0 步：前置校验 —— 目标表必须存在
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_guard_perf_indexes`;
DELIMITER $$
CREATE PROCEDURE `sp_guard_perf_indexes`()
BEGIN
    DECLARE v_tables INT DEFAULT 0;

    SELECT COUNT(*) INTO v_tables
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('product_spu', 'product_sku', 'order_info');

    IF v_tables <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '中止：缺少 product_spu / product_sku / order_info，请先执行 schema.sql';
    END IF;
END$$
DELIMITER ;

CALL `sp_guard_perf_indexes`();
DROP PROCEDURE `sp_guard_perf_indexes`;

-- ----------------------------------------------------------------------------
-- 第 1 步：按需建索引
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_index_if_absent`;
DELIMITER $$
CREATE PROCEDURE `sp_add_index_if_absent`(
    IN p_table   VARCHAR(64),
    IN p_index   VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('[已新增] ', p_table, '.', p_index) AS result;
    ELSE
        SELECT CONCAT('[已存在，跳过] ', p_table, '.', p_index) AS result;
    END IF;
END$$
DELIMITER ;

-- 商品列表：不带其它条件时的默认排序（WHERE deleted = 0 ORDER BY create_time DESC）
CALL `sp_add_index_if_absent`('product_spu', 'idx_deleted_create_time', '`deleted`, `create_time`');

-- 商品列表：按上下架状态过滤 + 默认排序
CALL `sp_add_index_if_absent`('product_spu', 'idx_status_create_time', '`status`, `create_time`');

-- 商品列表：按分类（含子孙，IN 查询）过滤 + 状态 + 默认排序
CALL `sp_add_index_if_absent`('product_spu', 'idx_category_status_create',
                              '`category_id`, `status`, `create_time`');

-- 商品详情：按 SPU 取 SKU 并按价格升序（现有 idx_spu_id 仍需回表排序）
CALL `sp_add_index_if_absent`('product_sku', 'idx_spu_price', '`spu_id`, `price`');

-- 我的订单：按用户 + 状态过滤 + 下单时间倒序
CALL `sp_add_index_if_absent`('order_info', 'idx_user_status_create',
                              '`user_id`, `status`, `create_time`');

-- 我的订单：不带状态过滤时的用户维度时间倒序（上面的复合索引无法满足该排序）
CALL `sp_add_index_if_absent`('order_info', 'idx_user_create', '`user_id`, `create_time`');

DROP PROCEDURE `sp_add_index_if_absent`;

-- ----------------------------------------------------------------------------
-- 第 2 步：结果核对（人工检查输出，不影响脚本成败）
-- ----------------------------------------------------------------------------
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('product_spu', 'product_sku', 'order_info')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

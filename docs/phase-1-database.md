# 阶段 1：数据库设计与基础实体层

> 目标：把「数据结构」这份长期契约先钉死，再往上写业务。本阶段还包含一次鉴权域重构（菜单/权限解耦）。

## 一、数据库设计

### 1.1 四个领域、14 张表

| 领域 | 表 |
|---|---|
| 用户权限（8） | `sys_user`、`sys_role`、`sys_menu`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_role_menu`、`user_address` |
| 商品（3） | `product_category`、`product_spu`、`product_sku` |
| 购物车（1） | `cart_item` |
| 订单（2） | `order_info`、`order_item` |

脚本：

| 文件 | 用途 |
|---|---|
| `src/main/resources/db/schema.sql` | 建库 + 14 张表 + 索引（**全量重建**，以 `DROP TABLE` 开头） |
| `src/main/resources/db/data.sql` | 角色 / 菜单 / 权限 / 分类 / 示例商品 / 种子管理员（**全量重建**） |

> ⚠️ 这两个脚本**只能用于全新环境**。已有数据的库一律走 `db/migration/` 增量脚本。

### 1.2 设计要点

| 决策 | 理由 |
|---|---|
| **物理无外键** | 外键会在大促写入路径上引入额外的锁与一致性检查；引用完整性由索引 + 应用层保证 |
| **金额 `DECIMAL(10,2)`** | 禁用 `float/double`，避免二进制浮点误差把 0.01 元算丢 |
| **逻辑删除 + 乐观锁统一预埋** | `BaseEntity` 统一提供 `deleted` / `version`，业务表不用各自定义 |
| **订单保存地址与商品快照** | 商品改价、地址修改不能改变历史订单 |
| **雪花 ID** | 单机自增在分库分表前就够，但 `AUTO_INCREMENT` 会把「分布式 ID」留成未来的返工点 |

### 1.3 逻辑删除与唯一键的坑

`@TableLogic` 只影响 MyBatis-Plus 自动生成的条件，**自定义 SQL 不会自动追加 `deleted = 0`**，
必须手写。同理，唯一键若不含 `deleted` 列，被逻辑删除的行仍占着唯一键 —— 详见
[phase-2-business.md 的加购 upsert 一节](phase-2-business.md#34-加购的-upsert为什么不能先查后插)。

## 二、实体与 Mapper

- 14 个 Entity：11 个业务实体继承 `BaseEntity`；3 个纯关联表（`sys_user_role`、`sys_role_permission`、`sys_role_menu`）不继承。
- 14 个 Mapper 接口。
- 两个自定义 `ResultMap` XML 作为两种典型写法的样板：
  - `ProductSpuMapper.xml`：`association` 平铺写法（SPU + SKU 列表进 `SpuDetailVO`）；
  - `OrderInfoMapper.xml`：`collection` 一对多写法（订单 + 明细进 `OrderDetailVO`）。

## 三、CRUD 垂直切片

以商品分类为样板跑通整条链路：
`CategoryController` → `CategoryService` → `CategoryServiceImpl` → `CategoryMapper`，
含分类树组装、分页 + 排序白名单、新增 / 更新 / 删除（逻辑删除）、`Entity ↔ VO` 的 MapStruct 转换。

## 四、迁移脚本规范

`db/migration/` 下的脚本是**唯一**允许对已有库执行的结构变更手段。

| 脚本 | 内容 | 影响范围 |
|---|---|---|
| `2026-10-13__split_menu_permission.sql` | 拆分 `sys_permission` 为菜单表 + 权限表，新增 `sys_role_menu` | 仅鉴权域 4 张表 |
| `2026-10-15__rename_permission_id_to_perm_id.sql` | `sys_role_permission.permission_id` → `perm_id` | 纯列名变更 |
| `2026-10-24__seed_admin_user.sql` | 种子管理员 `admin` + 绑定 `ADMIN` 角色 | 仅 `sys_user` / `sys_user_role` |

规范：

1. **前置校验**：数据不符合预期时主动 `SIGNAL` 中止，而非静默继续；
2. **回滚语句**写在脚本末尾注释里；
3. **备份策略**：涉及数据行增删改的迁移做整表备份（`bak_<日期>_<表名>`）；
   纯 DDL 元数据变更（改列名、改索引名）不备份 —— 数据行不移动，回滚 DDL 即可；
4. **已执行过的迁移脚本视为历史记录，不再回头修改**，后续变更一律新增脚本。

## 五、阶段 1 后重构：菜单与权限解耦（2026-10-13）

阶段 1 最初把「导航结构」和「权限点」塞进同一张 `sys_permission`（`type` 1/2/3 区分目录/菜单/按钮，
`perms` 挂在菜单行上）。三个硬伤：

1. **新增一个接口权限必须动菜单树** —— 一个纯后端校验（如「订单发货」）被迫在导航里占位；
2. **菜单行被迫携带权限语义** —— 目录行的 `perms` 只能填 `NULL`，语义靠约定而非结构表达；
3. **无法表达「能看见但不能操作」** —— 只读管理员要么多出写权限，要么丢掉菜单。

拆分后：

| 表 | 回答的问题 | 结构 | 授权链路 |
|---|---|---|---|
| `sys_menu` | 前端**能看见**哪些导航入口 | 树形（`parent_id` 自关联，二级） | `sys_role_menu` |
| `sys_permission` | 后端**能执行**什么操作 | 扁平（无 `parent_id`，`perms` 唯一） | `sys_role_permission` |

**两条链路彼此独立、互不推导**：挂上菜单不等于拥有该菜单下所有按钮权限，反之亦然。
`MenuPermissionSplitIntegrationTest` 里有一条断言专门守住这点 —— 两条链路的 ID 空间不允许出现交集。

`sys_permission.perms` 的唯一约束是刻意加的：同一标识存两行会导致「角色挂了其中一行、鉴权比对另一行」的静默失效。

### 关联表列名约定（2026-10-15）

| 关联表 | 列 |
|---|---|
| `sys_role_permission` | `role_id` / `perm_id` |
| `sys_role_menu` | `role_id` / `menu_id` |

主表主键仍统一叫 `id`（`BaseEntity` 约定），JOIN 写作 `ON srp.perm_id = p.id`。
把主键列改名意味着该实体不能再继承 `BaseEntity`，为一张表放弃整个项目的实体基类统一性，代价大于收益。

## 六、阶段 1 的边界

- 只做**分类**这一个模块的完整 CRUD；商品 / 购物车 / 订单留给阶段 2。
- 没有鉴权：`UserContext` 尚不存在，接口全部匿名可调。

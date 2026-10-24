# 阶段 2：核心业务模块（商品 / 购物车 / 订单）

> 目标：跑通「浏览 → 加购 → 下单 → 取消/发货/收货」这条主链路，并把并发与事务的边界写对。

## 一、模块清单

### 1.1 商品（`SpuController` → `SpuService`）

分页（分类含子孙展开、关键字、价格区间、状态、排序白名单）、详情（含 SKU 列表）、
新增 / 更新（SKU 全量差异同步）/ 上下架 / 逻辑删除。

对外统一走 `SpuVO` / `SpuDetailVO` / `SkuVO`。`SpuDetailVO` 从「继承实体」改为独立 POJO ——
继承会让 `deleted` / `version` 顺着响应体漏出去。

### 1.2 购物车（`CartController` → `CartService`）

加购（同 SKU 累加 + 复活 + 原子封顶）、列表（实时价格与库存、失效标记）、改量、批量删除、
勾选 / 全选、下单前清理已勾选。

### 1.3 订单（`OrderController` → `OrderService`）

提交（购物车 / 立即购买双来源）、下单前预览（运费与应付金额，只读）、我的订单分页、
详情（自定义 ResultMap 一对多）、取消（回滚库存）、发货、确认收货。

金额规则：满 99 元包邮，否则固定 12 元运费。

## 二、基础设施

- `ResultCode` 补齐业务码：商品 2001-2005、购物车 3001-3003、订单 4001-4004；
- `OrderStatusEnum`（含状态机判定方法与文案）、`ProductStatusEnum`、`OrderSourceEnum`；
- `UserContext` + `UserContextInterceptor` + `WebMvcConfig`（阶段 3 已改接 Sa-Token）；
- `OrderNoGenerator`、`CartConstants` / `OrderConstants`。

## 三、关键设计

### 3.1 库存扣减：条件原子 UPDATE

`ProductSkuMapper.deductStock` 用一条语句同时完成「够不够」与「扣多少」：

```sql
UPDATE product_sku
SET stock = stock - #{quantity}, sales = sales + #{quantity},
    version = version + 1, update_time = NOW()
WHERE id = #{skuId} AND deleted = 0 AND stock >= #{quantity}
```

| 维度 | 条件原子 UPDATE（采用） | `@Version` + 重试 |
|---|---|---|
| 数据库往返 | 1 次 | 至少 2 次，冲突后还要再查再改 |
| 超卖防护 | `WHERE stock >= ?` 在行锁内保证 | 应用层判断，判断与写入之间有窗口 |
| 失败分支 | 只有「库存不足」一种明确语义 | 需区分「库存不足」与「版本冲突」，重试耗尽时语义模糊 |
| 锁等待 | 单行写锁，微秒级 | 同样有行锁，外加更多往返 |

`version` 字段保留给需要「读-改-写」语义的更新（如商品编辑）。

**扣减顺序按 SKU ID 升序**：多商品订单并发下单时，若两个事务以相反顺序锁同一批行会形成死锁；
统一顺序后后来的事务只需等待，不会成环。

**验证方式**：两个用户各持一份含 `[SKU-A, SKU-B]` 的购物车，把 SKU-B 库存压到 1，并发提交。
结果应为「一人成功、一人 2005」，且 SKU-A 只被扣 1 件 —— 败者已执行的扣减随事务一并回滚。
若事务边界写错，SKU-A 会被扣 2 件。

### 3.2 订单号

`OrderNoGenerator` 生成，唯一约束兜底。订单号对用户可见，不暴露自增/雪花序列的规模信息。

### 3.3 状态机

状态流转集中在 `OrderStatusEnum` 内闭合，Service 只调用判定方法，不在业务代码里散落
`if (status == 1)` 这类魔法值判断。

### 3.4 加购的 upsert：为什么不能「先查后插」

`cart_item` 上有唯一键 `uk_user_sku(user_id, sku_id)`，而它**不包含 `deleted` 列** ——
被逻辑删除的条目仍然占着这个唯一键。于是：

- 「`selectOne` 查有没有（`@TableLogic` 自动过滤已删除行）→ 没有则 `insert`」的写法，
  在「加入购物车 → 删除 → 再加购」时必然撞唯一键，抛 `DuplicateKeyException` 变成 5000 系统异常；
- 这个 bug 只在「删过再加」的路径上出现，自测时极易漏掉。

因此改用 `INSERT ... ON DUPLICATE KEY UPDATE`（MySQL 8.0.19+ 行别名语法 `AS new`），
一条语句表达「插入 / 累加 / 复活」三种语义，并用 `LEAST(..., 999)` 在 SQL 内原子封顶数量 ——
「累加 + 上限校验」若分两步做，并发下两个请求可能各自校验通过后再累加，突破上限。

### 3.5 阶段 2 的临时身份上下文（已被阶段 3 取代）

购物车与订单强依赖「当前用户」。阶段 3 之前没有 Sa-Token，`UserContextInterceptor`
从 `X-User-Id` 请求头取值写入 `UserContext`（ThreadLocal），`afterCompletion` 中清除 ——
Tomcat 线程是复用的，不清除会让下一个请求读到上一个用户的 ID。

**这是临时方案，不具备任何安全性**：客户端可以随意伪造 `X-User-Id`。

Service 层的方法签名一律**显式接收 `userId` 参数**而不是内部读 ThreadLocal：
后者在异步线程、定时任务里会拿到 null，而显式传参让调用方无法「忘记」它。
这条约定在阶段 3 换成 Sa-Token 取值后依然保留。

## 四、测试

`CartOrderIntegrationTest`（22 个用例）覆盖：

- 加购累加 / 复活 / 封顶；
- 用户数据隔离；
- 下架商品失效标记；
- 下单扣减；
- 运费门槛（含「刚好 99 元」的标度边界）；
- 库存不足时的事务回滚；
- 取消的幂等回滚；
- 完整状态机流转；
- 明细快照不可变；
- 预览只读；
- 订单号唯一。

## 五、阶段 2 的边界（明确不做的事）

1. **支付属于阶段 5**。订单状态机里的「已支付」在本阶段没有接口能触发，
   集成测试通过直接把状态置为 `PAID` 来验证后续流转；`payTime` 因此为 null。
2. **发货接口未鉴权**。任何能伪造 `X-User-Id` 的调用者都能发货 ——
   阶段 3 挂上 `order:info:deliver` 权限点后已修复。
3. **订单超时关闭**（60 分钟）需要定时任务，属阶段 5。

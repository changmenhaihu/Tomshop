# 阶段 4：多级缓存与性能优化

> 目标：把「每次请求都打数据库」的高频读路径改造成 Caffeine + Redis 两级缓存，
> 并补齐商品列表 / SKU / 订单查询的复合索引。
> **本阶段不新增接口**，只改读路径的性能与数据一致性策略。

## 一、做了什么、不做什么

| 做 | 不做（以及原因） |
|---|---|
| 分类树、分类父子映射、商品详情走两级缓存 | **商品列表分页不缓存**：键的基数随筛选条件爆炸，失效成本远高于收益 |
| 空值缓存、分布式锁回源、TTL 抖动 | 不做「更新缓存」：并发写时更新顺序可能与数据库提交顺序相反，删除才是幂等的 |
| 事务提交后失效 + 延迟二次删除 + pub/sub 广播清 L1 | 不做缓存与数据库的强一致：目标由「最终一致 + 短 TTL」兜底，不引入分布式事务 |
| 6 条复合索引覆盖高频「过滤 + 排序」 | 不为价格 / 销量排序建索引：会与分类、状态组合出大量索引，写入成本不可控 |

## 二、技术选型

| 组件 | 版本 | 作用 |
|---|---|---|
| Caffeine | 2.9.3 | L1，进程内缓存，纳秒级读取 |
| Redis（现有 Lettuce） | 5.0.14 | L2，跨实例共享、重启不丢 |
| Redisson | 3.31.0 | 仅用于缓存击穿保护的分布式锁 |

> **版本说明**：本机处于离线环境，`G:/maven-repo` 中可用的最新版本是 Caffeine 2.9.3 与
> Redisson 3.31.0，因此没有使用 3.1.x / 3.34.x。两者在本项目用到的 API 上无差异，
> 联网后可直接升版本号，不需要改代码。
> 另外网络原因导致 `netty-resolver-dns` 只有 4.1.113，POM 中显式对齐了 `<netty.version>`；
> Redisson 的 `rxjava` 传递版本同理显式降为 3.1.8。

**为什么不引 `redisson-spring-boot-starter`**：它会用 Redisson 顶替 Boot 自动装配的 Lettuce
连接工厂，并替换 Spring Data Redis 的序列化配置，与现有的 `RedisConfig`、Sa-Token 的
`StringRedisTemplate` 形成隐性耦合。本项目只需要一把锁，自己构建 `RedissonClient` 更可控。

**为什么 Redisson 懒初始化**：阶段 0 定下的性质是「应用启动不依赖中间件可用性」。
若在构造期 `Redisson.create()`，Redis 未启动会让应用起不来。
现在改为首次加锁时才建客户端；失败则降级为无锁直查，功能仍可用。

## 三、缓存结构

```
请求 → L1 Caffeine（进程内）
          │ 未命中
          ▼
       L2 Redis（跨实例）
          │ 未命中
          ▼
       Redisson 锁 → 二次检查 L2 → 查数据库 → 回填 L2 + L1
```

| 缓存键 | 内容 | L1 TTL | L2 TTL | 失效时机 |
|---|---|---|---|---|
| `category:tree` | 上架分类树（含 children） | 5 分钟 | 30 分钟 | 分类增 / 改 / 删提交后 |
| `category:children-map` | `父 id → 子 id 列表` | 5 分钟 | 30 分钟 | 同上 |
| `product:detail:{spuId}` | 商品详情 + SKU 列表 | 60 秒 | 5 分钟 | 商品改 / 上下架 / 删提交后 |
| 空值标记 | `__TOMSHOP_CACHE_NULL__` | 60 秒 | 60 秒 | 随数据写入自然覆盖 |

> 商品详情的 TTL 刻意短：详情里带 SKU 库存，而下单扣库存不会逐个失效商品详情
> （一次下单会触发大量缓存删除）。真正的库存校验在加购 / 下单路径上，**那条路径不走缓存**。

键的拼装统一收口在 `CacheKeys`，Redis 前缀 `tomshop:cache:v1:` 由 `MultiLevelCache` 统一加。
版本号写在键前缀里：缓存格式不兼容时改版本号即可整体失效，不需要去 Redis 扫键。

## 四、三个经典问题

| 问题 | 处理 |
|---|---|
| **穿透**（查不存在的 ID） | loader 返回 `null` 时写入空值标记，短 TTL 60 秒；L1 用哨兵对象表示空值（Caffeine 不允许存 null） |
| **击穿**（热点键过期瞬间并发回源） | 回源前抢 Redisson 锁；抢到的线程查库回填，其余线程等锁后二次检查 L2。等锁超时则**降级直查且不写缓存**，避免请求线程全部堵在锁上 |
| **雪崩**（同一批键同时过期） | L2 TTL 叠加 ±10% 随机抖动（`ttl-jitter-ratio`），打散过期时刻 |

降级路径不写缓存是刻意的：抢锁失败者可能读到旧值，把它写回去会把旧数据再固化一个 TTL。

## 五、一致性：更新数据库 → 提交后删缓存

### 5.1 为什么必须「提交后」删

若在事务提交前删除，并发读会在「缓存已删、数据未提交」的窗口里读到旧值并重新写回，
旧值反而被固化。`MultiLevelCache.evictAfterCommit` 注册
`TransactionSynchronization.afterCommit`：

- 事务提交 → 执行删除；
- 事务回滚 → 什么都不做；
- 无事务上下文 → 立即执行（便于非事务场景与运维脚本调用）。

### 5.2 为什么延迟再删一次

仍有「读-删-回填」竞态：某读请求在写事务提交前未命中、查到了旧值，却在本实例删缓存后
才把旧值写进 L2。提交后 500ms 的第二次删除覆盖这个窗口。
这是**有界补偿**，不是分布式事务；剩余窗口由短 TTL 兜底。

### 5.3 多实例的 L1

A 实例更新后只能删掉自己的 L1 和共享的 Redis，B 实例的 L1 仍会返回旧值。
因此删除后向 `tomshop:cache:invalidate` 发布键名，各实例收到后只清自己的 L1（消息体只放键名，
不传数据本身）。pub/sub 是至多一次投递，断线期间的消息会丢 —— 所以它只负责「及时」，
正确性的最终兜底仍是 L1 的短 TTL。

## 六、缓存故障策略

原则：**缓存永远不能拖垮业务**。

- L2 读 / 写 / 删除 / 广播的异常一律只记日志并降级（读 → 回源，写 → 跳过），绝不向上抛；
- Redis 报错后按 `remote-retry-backoff-millis`（默认 5 秒）熔断，期间直接当作未命中，
  避免每个请求都去撞一个已确认不可用的连接；
- 熔断窗口刚开启时只打一条 warn，不刷屏。

## 七、索引优化

迁移脚本：[`src/main/resources/db/migration/2026-10-24__add_query_indexes.sql`](../src/main/resources/db/migration/2026-10-24__add_query_indexes.sql)

脚本是幂等的（先查 `information_schema.statistics`，已存在则跳过），只新增二级索引，
不改表结构。`schema.sql` 已同步这 6 条索引，全新环境无需再单独执行。

| 表 | 索引 | 覆盖的查询 |
|---|---|---|
| `product_spu` | `(deleted, create_time)` | 商品列表默认排序（不带状态过滤） |
| `product_spu` | `(status, create_time)` | 商品列表按上下架过滤 + 默认排序 |
| `product_spu` | `(category_id, status, create_time)` | 单分类筛选 + 状态 + 默认排序 |
| `product_sku` | `(spu_id, price)` | 商品详情取 SKU 并按价格升序 |
| `order_info` | `(user_id, status, create_time)` | 我的订单按状态筛选 + 时间倒序 |
| `order_info` | `(user_id, create_time)` | 我的订单不带状态筛选时的时间倒序 |

### 7.1 EXPLAIN 验证（2026-10-24）

开发库只有几条数据，`EXPLAIN` 给出的执行计划没有参考价值。
验证在临时库中进行：复制三张表的结构，灌入 `product_spu` 3 万、`product_sku` 6 万、
`order_info` 5 万行（含 10% 下架、2% 逻辑删除），`ANALYZE TABLE` 后执行 `EXPLAIN`，验证完即删库。
**未向开发库写入任何测试数据。**

| 查询 | 使用的索引 | Extra |
|---|---|---|
| 列表：`deleted=0 AND status=1 ORDER BY create_time DESC, id DESC` | `idx_status_create_time` | `Backward index scan`（无 filesort） |
| 列表：`deleted=0 AND category_id=1 AND status=1 ORDER BY ...` | `idx_category_status_create` | `Backward index scan` |
| 列表：`deleted=0 AND category_id IN (1,2) AND status=1 ORDER BY ...` | `idx_deleted_create_time`（优化器按顺序扫描取前 10 条） | `Using index condition`，无 filesort |
| 列表：`deleted=0 ORDER BY create_time DESC, id DESC` | `idx_deleted_create_time` | `Backward index scan` |
| SKU：`spu_id=1001 ORDER BY price ASC, id ASC` | `idx_spu_price` | 无 filesort |
| 订单：`user_id=10 AND status=10 ORDER BY create_time DESC, id DESC` | `idx_user_status_create` | `Backward index scan` |
| 订单：`user_id=10 ORDER BY create_time DESC, id DESC` | `idx_user_create` | `Backward index scan` |

关键点：排序写成 `ORDER BY create_time DESC, id DESC` 时，二级索引叶子节点天然按
`(create_time, id)` 有序，倒序扫描即可满足，`id` 这个兜底排序键不会引入 filesort。
如果写成 `create_time DESC, id ASC`，排序方向不一致，filesort 会回来。

### 7.2 已知限制

- **`category_id IN (...)` + 排序**：多分类 IN 时索引无法同时满足所有分类分支的有序性，
  优化器可能改走时间索引顺序扫描。数据量在十万级以内、`LIMIT` 较小时影响可忽略；
  单分类（最常见）可完整走复合索引。
- **按价格 / 销量排序**：`EXPLAIN` 仍是 `Using filesort`。为它建索引需要覆盖每种
  分类 × 状态组合，索引数量与写入放大不可接受。商品量级达到十万级以上时，
  应引入 Elasticsearch，而不是继续叠索引。

## 八、配置项

```yaml
tomshop:
  cache:
    enabled: true
    key-prefix: "tomshop:cache:v1:"       # 改版本号 = 整体切代
    invalidation-channel: "tomshop:cache:invalidate"
    local-maximum-size: 10000             # L1 最大条目数
    local-ttl-seconds: 120                # 默认策略（未显式传 CachePolicy 时）
    remote-ttl-seconds: 600
    null-ttl-seconds: 60
    ttl-jitter-ratio: 0.1
    delayed-evict-millis: 500             # <=0 关闭延迟二次删除
    lock-wait-seconds: 2
    lock-lease-seconds: 10
    remote-retry-backoff-millis: 5000
```

`enabled=false` 时 `getOrLoad` 直接回源，不读不写任何缓存，便于线上排障时快速摘除缓存层。

## 九、测试

`CacheIntegrationTest`（7 个用例）：

| 用例 | 验证点 |
|---|---|
| 读路径 | 回源一次 → L1 命中 → 清 L1 后 L2 命中；L2 TTL 落在抖动区间 |
| 空值缓存 | loader 返回 null 后，L1 与 L2 都能拦截，且 Redis 中存的是空值标记 |
| 提交后失效 | 事务内缓存仍命中（证明没提前删）；提交后重新回源 |
| 回滚不失效 | 回滚后缓存原样保留 |
| 广播清理 | 监听器只清本机 L1，L2 不受影响 |
| 击穿降级 | 另一线程持锁时直接回源、不写缓存、不阻塞 |
| 业务接入 | 分类新增 / 删除后，分类树缓存被正确失效 |

**本类不能加 `@Transactional`**：测试级事务永远不会提交，`afterCommit` 不触发，
测出来的「通过」是假通过。因此用例真实提交，在 `@AfterEach` 清理 Redis 键，
测试分类在 `finally` 中物理删除，不污染开发库。

全项目回归：**51 个用例通过**（阶段 3 的 44 个 + 本阶段 7 个）。

## 十、后续可做

- `CacheStats` 目前只提供运行期统计，尚未接入 Micrometer / Actuator；
- 延迟二次删除的 500ms 是经验值，可在压测后按实际读回填延迟调整；
- 列表缓存的替代方案是「热门榜 / 首页推荐」这类键基数可控的场景，届时单独设计。

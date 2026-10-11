# TomShop

前后端分离 B2C 电商单体后端。

> **当前进度：阶段 0（项目初始化与基础骨架）已完成** —— 详见文末「开发进度」。

---

## 一、技术栈

| 分类 | 选型 | 版本 | 引入阶段 |
|---|---|---|---|
| 语言 / 运行时 | Java（编译级别 `release=17`） | 17 | 0 |
| 框架 | Spring Boot | 3.3.5 | 0 |
| ORM | MyBatis-Plus | 3.5.9 | 0 |
| 数据库 | MySQL + HikariCP | 8.x | 0 |
| 缓存 | Redis（Lettuce）+ Caffeine + Redisson | 3.x / 3.1.8 / 3.34.1 | 0 / 4 / 4 |
| 安全 | Shiro + 双 JWT | 2.0.2 / jjwt 0.12.6 | 3 |
| 消息 | RocketMQ | 2.3.1 | 5 |
| 网络 | Netty | 4.1.x | 5 |
| 文档 | Knife4j（OpenAPI 3） | 4.5.0 + springdoc 2.6.0 | 0 |
| 代码工具 | Lombok / MapStruct / Commons / Jackson | — | 0 |

**依赖引入策略**：按阶段增量引入。每阶段结束时项目必须能独立启动、独立验证，
因此阶段 0 不引入 Shiro / Redisson / RocketMQ / Netty —— 这些 starter 一进 POM 就会触发自动装配并尝试连接中间件，
会让「项目可正常启动」这条验收标准与中间件可用性耦合。

---

## 二、环境要求

| 项 | 要求 | 本机实况 |
|---|---|---|
| JDK | 17+ | `G:/develop/jdk`（Temurin 21.0.11）；`java` 命令在 PATH 上解析到 JDK 18，构建时请显式指定 `JAVA_HOME` |
| Maven | 3.8+ | `G:/develop/apache-maven-3.9.16`，本地仓库 `G:/maven-repo` |
| MySQL | 8.0+ | 已建库 `tomshop`（8.0.44），本机 root 密码见 `application-dev.yml` 的 `MYSQL_PASSWORD` 默认值 |
| Redis | 6.0+ | 默认 `localhost:6379`，无密码 |

### 初始化数据库（首次运行前执行一次）

```bash
MYSQL="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
# 建库 + 建表（12 张）
"$MYSQL" -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
# 初始化数据（角色/权限/分类/示例商品）
"$MYSQL" -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 tomshop < src/main/resources/db/data.sql
```

### 构建与启动

```bash
# 显式指定 JDK 21，避免误用 PATH 上的 JDK 18
export JAVA_HOME="G:/develop/jdk"

# 编译
mvn -q clean compile

# 运行测试（含 ResultMap 集成测试，需 MySQL + Redis 可用）
mvn -q test

# 启动（dev 环境）
mvn spring-boot:run
```

启动后访问：

| 地址 | 说明 |
|---|---|
| `http://localhost:8080/api/v1/health` | 业务探活，返回应用信息 + MySQL/Redis 实时连通状态 |
| `http://localhost:8080/actuator/health` | Actuator 标准探活，供容器探针使用 |
| `http://localhost:8080/doc.html` | Knife4j 接口文档 |

> 阶段 0 的探活接口**不依赖中间件可用性**：MySQL 或 Redis 未启动时应用仍能正常启动，
> 探活接口会返回 `status = DEGRADED` 并在日志中给出 warn 提示。这是刻意设计——探活接口的职责是报告状态，而不是把中间件故障升级为启动失败。

---

## 三、项目结构

```
Tomshop/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/tom/tomshop/
    │   ├── TomShopApplication.java      启动类（@MapperScan）
    │   ├── common/
    │   │   ├── base/         BaseEntity（审计字段+逻辑删除+乐观锁）、PageQuery
    │   │   ├── constant/     CommonConstants（全局常量唯一来源）
    │   │   ├── enums/        业务枚举
    │   │   └── result/       Result、ResultCode、PageResult
    │   ├── config/
    │   │   ├── properties/   TomShopProperties（tomshop.* 配置绑定）
    │   │   ├── CorsConfig        跨域
    │   │   ├── JacksonConfig     Web 层序列化（委托 JsonUtils 统一规则）
    │   │   ├── Knife4jConfig     接口文档
    │   │   ├── MybatisPlusConfig 分页/乐观锁/防全表更新
    │   │   ├── MybatisMetaObjectHandler  公共字段自动填充
    │   │   └── RedisConfig       RedisTemplate 序列化
    │   ├── controller/   → service/ → mapper/
    │   ├── dto/  vo/  convert/   MapStruct 转换器
    │   ├── exception/    BusinessException、GlobalExceptionHandler
    │   └── utils/        JsonUtils、PageUtils
    └── resources/
        ├── application.yml + application-dev.yml
        ├── logback-spring.xml
        ├── mapper/     自定义 ResultMap XML（阶段 1 起使用）
        └── db/         建表与初始化 SQL（阶段 1）
```

**分层纪律**（全项目强制）：

1. `Controller` 只做「接参 → 校验 → 转发 → 包装返回值」。不注入 Mapper、不写业务分支、不加事务注解。
2. 业务逻辑全部在 `Service`，`ServiceImpl` 不出现 `HttpServletRequest`、`Result` 等 Web 类型。
3. 对外只返回 `VO`，绝不返回 `Entity`——后者会泄露表结构、逻辑删除标记与乐观锁版本号。
4. `Entity ↔ DTO ↔ VO` 的转换一律由 MapStruct 转换器承担，禁止手写逐字段 `set`。

---

## 四、核心设计说明

### 4.1 统一响应与异常

所有接口返回 `Result<T>`，`code = 200` 为业务成功。HTTP 状态码统一保持 200，由业务码驱动前端行为，
避免前端同时解析 HTTP 状态与响应体两套语义。

`GlobalExceptionHandler` 的分类处理原则：**可预期的业务分支记 warn 不打堆栈**（`BusinessException`、参数校验失败），
**未预期异常记 error 并保留完整堆栈**（兜底 `Exception`），且对外只暴露通用文案，堆栈留在服务端日志。

### 4.2 雪花 ID 的精度保护（关键约定）

Java 雪花 ID 为 19 位 `long`，超出 JS `Number.MAX_SAFE_INTEGER`（2^53-1，约 16 位）。
若直接以数字下发，前端会**静默**丢精度——表现为「列表里看到的 ID 和点进去的详情对不上」，
这类 bug 极难排查。

因此在 `JsonUtils.applyTo()` 中统一约定：

| 类型 | JSON 表现 | 使用场景 |
|---|---|---|
| 包装类型 `Long` | **字符串** | 所有 ID 字段 |
| 基本类型 `long` / `int` | 数字 | 计数、页码等统计字段 |

**编码约定**：ID 一律用包装类型 `Long`，计数一律用基本类型 `long`。
这也是 `PageResult.total/pages` 使用 `long` 而非 `Long` 的原因。

### 4.3 静态资源与中间件解耦

- 关闭 MyBatis 二级缓存（`cache-enabled: false`）。其失效粒度基于命名空间，跨命名空间写操作不会互相失效，
  在库存/订单场景下是事故源。缓存统一由阶段 4 的 Caffeine + Redis 承担。
- Hikari `connection-timeout` 设为 5s（非默认 30s），因为它同时是「数据库宕机时探活接口的最坏耗时」。

### 4.4 公共字段自动填充

`BaseEntity` 的 `createTime` / `updateTime` / `deleted` / `version` 由 `MybatisMetaObjectHandler` 自动填充，
业务代码不要手工 set。使用 `strictInsertFill` 语义：仅在字段为 null 时填充，
因此数据迁移等需要保留原始 `createTime` 的场景不会被覆盖。

---

## 五、编码约定

| 约束 | 说明 |
|---|---|
| 常量唯一来源 | 全局常量放 `CommonConstants`，模块常量放同包独立常量类。禁止在多处重复定义同一语义的值 |
| 禁止魔法值 | 状态、类型、标记一律用枚举或常量 |
| 排序字段 | `PageQuery.sortField` 拼入 `ORDER BY` 前**必须**经 `resolveSortField(白名单...)` 校验 |
| 敏感配置 | 密码、密钥走环境变量（`${ENV:default}`），不硬编码 |
| 日志 | 用 `@Slf4j`，占位符 `{}` 而非字符串拼接；异常日志带 uri + method |
| 参数校验 | Controller 入参一律 `@Validated` / `@Valid` |
| 日期格式 | 统一走 `CommonConstants` 中的 pattern 常量，禁止各处硬编码 |

---

## 六、开发进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| **0** | 项目初始化与基础骨架 | ✅ 已完成 |
| **1** | 数据库设计与基础实体层 | ✅ 已完成 |
| 2 | 核心业务模块（商品 / 购物车 / 订单） | ⏳ 待开始 |
| 3 | 权限安全体系（Shiro + Redis + 双 JWT） | ⏳ |
| 4 | 多级缓存与性能优化 | ⏳ |
| 5 | 并发、消息与在线客服 | ⏳ |
| 6 | 效率提升、文档与收尾 | ⏳ |

### 阶段 0 交付物

统一返回（`Result` / `ResultCode` / `PageResult`）、实体基类（`BaseEntity` / `PageQuery`）、
全局异常处理、CORS / Jackson / MyBatis-Plus / Redis / Knife4j 配置、`JsonUtils` / `PageUtils` 工具类、
多环境 YAML 与 Logback 配置、健康检查接口（`GET /api/v1/health`）。

### 阶段 1 交付物

**数据库**：`db/schema.sql`（建库 + 12 张表 + 索引）、`db/data.sql`（角色/权限/分类/示例商品）。
12 张表分四域：用户权限域（sys_user / sys_role / sys_permission / sys_user_role /
sys_role_permission / user_address）、商品域（product_category / product_spu / product_sku）、
购物车域（cart_item）、订单域（order_info / order_item）。

**代码层**：12 个 Entity（10 个业务实体继承 BaseEntity，2 个关联表实体不继承）、
12 个 Mapper 接口、两个自定义 ResultMap 示例（`ProductSpuMapper.xml` 的 association 平铺写法、
`OrderInfoMapper.xml` 的 collection 一对多写法，配套 `SpuDetailVO` / `OrderDetailVO`）。

**CRUD 垂直切片**：商品分类模块（`CategoryController` → `CategoryService` → `CategoryServiceImpl`），
含分类树组装、分页 + 排序白名单、新增/更新/删除（逻辑删除）。

**设计要点**：物理无外键（索引 + 应用层保证引用）、金额 DECIMAL(10,2)、订单收货/商品快照、
逻辑删除 + 乐观锁统一预埋、雪花 ID 序列化精度保护。

### 已解决的版本陷阱

1. **MyBatis-Plus 3.5.9 拆包**：自 3.5.9 起 jsqlparser 被移出核心包改为可选依赖，
   `PaginationInnerInterceptor`（分页）与 `BlockAttackInnerInterceptor`（防全表更新）随之迁至
   独立的 `mybatis-plus-jsqlparser` 适配包；而 `OptimisticLockerInnerInterceptor` 不依赖 SQL 解析，
   仍留在核心包内。**症状**：编译期报「找不到符号」，且只报分页与防全表更新这两个类，乐观锁正常——
   这个「报一半」的现象很容易被误判为依赖版本写错。POM 中已显式引入该适配包。
2. **Knife4j × Spring Boot 3.3**：knife4j 4.5.0 传递的 springdoc 2.3.0 面向 Spring Boot 3.2，
   已在 POM 中显式提升至 springdoc 2.6.0。实测 `/doc.html` 与 `/v3/api-docs` 均返回 200。
3. **JDBC `characterEncoding=utf8mb4` 非法**：MySQL Connector/J 的 `characterEncoding` 参数只认
   Java 字符集名，应写 `UTF-8`（Connector/J 8.x 自动映射到 utf8mb4）；写 `utf8mb4` 会抛
   `Unsupported character encoding 'utf8mb4'` 并导致 `CannotGetJdbcConnectionException`。
   `utf8mb4` 是 mysql 客户端/服务器端的叫法，不是 JDBC 参数合法值。已在 `application-dev.yml` 修正。

### 待验证的技术风险

1. **RocketMQ × Spring Boot 3**（阶段 5）：`rocketmq-spring-boot-starter 2.3.1` 的自动装配仍基于 Spring Boot 2.x 的
   `spring.factories` 机制，大概率需手工补 `AutoConfiguration.imports`。若不可行，退化为直接用 `rocketmq-client 5.x` 手写配置类。

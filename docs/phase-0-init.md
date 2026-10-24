# 阶段 0：项目初始化与基础骨架

> 目标：项目能独立启动、独立探活，且**不依赖任何中间件的可用性**。后续所有阶段在此骨架上增量叠加。

## 一、本阶段范围

| 项 | 交付物 |
|---|---|
| 工程骨架 | Maven 单模块、Spring Boot 3.3.5、`TomShopApplication`（`@MapperScan`） |
| 统一响应 | `Result<T>`、`ResultCode`、`PageResult<T>` |
| 实体基类 | `BaseEntity`（审计字段 + 逻辑删除 + 乐观锁）、`PageQuery` |
| 全局异常 | `BusinessException` + `GlobalExceptionHandler` |
| 基础设施配置 | CORS、Jackson、MyBatis-Plus、Redis、Knife4j、Logback、多环境 YAML |
| 工具类 | `JsonUtils`、`PageUtils` |
| 探活 | `GET /api/v1/health`、`/actuator/health` |
| 接口文档 | `GET /doc.html`（Knife4j） |

## 二、关键设计

### 2.1 依赖按阶段增量引入

阶段 0 **只引入**能独立启动的依赖，Sa-Token / Redisson / RocketMQ / Netty 全部留到所属阶段。
原因：这些 starter 一旦进入 POM 就会触发自动装配并尝试连接中间件，
「项目可正常启动」这条验收标准会被中间件可用性绑架。

### 2.2 统一响应与异常

所有接口返回 `Result<T>`，业务成功 `code = 200`。

`GlobalExceptionHandler` 的处理原则：

- **可预期的业务分支记 warn、不打堆栈**（`BusinessException`、参数校验失败）；
- **未预期异常记 error、保留完整堆栈**（兜底 `Exception`），对外只暴露通用文案。

> 阶段 3 起有一处例外：401/403 会同步设置 HTTP 状态码，见 [phase-3-auth.md](phase-3-auth.md#七异常与-http-状态的对应关系)。

### 2.3 雪花 ID 的精度保护

Java 雪花 ID 是 19 位 `long`，超过 JS `Number.MAX_SAFE_INTEGER`（2^53-1，约 16 位）。
直接以数字下发会让前端**静默**丢精度，表现为「列表里看到的 ID 和详情接口对不上」，极难排查。

在 `JsonUtils.applyTo()` 中统一约定：

| 类型 | JSON 表现 | 使用场景 |
|---|---|---|
| 包装类型 `Long` | **字符串** | 所有 ID 字段 |
| 基本类型 `long` / `int` | 数字 | 计数、页码等统计字段 |

**编码约定**：ID 一律用包装类型 `Long`，计数一律用基本类型 `long`。

### 2.4 与中间件解耦的设计

- 关闭 MyBatis 二级缓存（`cache-enabled: false`）：其失效粒度是命名空间，跨命名空间写操作不会互相失效，
  在库存/订单场景下是事故源。缓存统一由阶段 4 的 Caffeine + Redis 承担。
- Hikari `connection-timeout` 设为 5s（非默认 30s）——它同时是「数据库宕机时探活接口的最坏耗时」。
- 探活接口在 MySQL / Redis 不可用时仍返回 200，响应体 `status = DEGRADED` 并记录 warn。
  **探活接口的职责是报告状态，而不是把中间件故障升级为启动失败。**

## 三、验收标准

1. `mvn clean compile` 通过；
2. 无中间件时应用能启动，`/api/v1/health` 返回 `DEGRADED`；
3. MySQL + Redis 就绪时 `/api/v1/health` 返回 `UP`；
4. `/doc.html`、`/v3/api-docs` 返回 200。

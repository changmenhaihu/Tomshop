# TomShop

前后端分离 B2C 电商单体后端。

> **当前进度：阶段 0、1、2、3 已完成。** 阶段详情见 [docs/](docs/README.md)，接口清单见 [docs/api.md](docs/api.md)。

技术栈：Spring Boot 3.3.5 + MyBatis-Plus 3.5.9 + MySQL 8 + Redis + Sa-Token（双 JWT）。
按阶段增量交付，每个阶段结束时项目都能独立启动、独立验证。

---

## 一、技术栈

| 分类 | 选型 | 版本 | 引入阶段 |
|---|---|---|---|
| 语言 / 运行时 | Java（编译级别 `release=17`） | 17 | 0 |
| 框架 | Spring Boot | 3.3.5 | 0 |
| ORM | MyBatis-Plus | 3.5.9 | 0 |
| 数据库 | MySQL + HikariCP | 8.x | 0 |
| 缓存 | Redis（Lettuce）+ Caffeine + Redisson | 3.x / 3.1.8 / 3.34.1 | 0 / 4 / 4 |
| 安全 | Sa-Token + 双 JWT + BCrypt | 1.46.0 | 3 |
| 消息 | RocketMQ | 2.3.1 | 5 |
| 网络 | Netty | 4.1.x | 5 |
| 文档 | Knife4j（OpenAPI 3） | 4.5.0 + springdoc 2.6.0 | 0 |
| 代码工具 | Lombok / MapStruct / Commons / Jackson | — | 0 |

**依赖引入策略**：按阶段增量引入。阶段 0 不引入 Sa-Token / Redisson / RocketMQ / Netty ——
这些 starter 一进 POM 就会触发自动装配并尝试连接中间件，会让「项目可正常启动」
这条验收标准与中间件可用性耦合。

**安全框架**：原计划的 Shiro 已弃用，改用 Sa-Token（选型理由见
[phase-3-auth.md](docs/phase-3-auth.md#一为什么是-sa-token)）。Spring Boot 3 必须用
`sa-token-spring-boot3-starter`。

---

## 二、环境要求

| 项 | 要求 | 本机实况 |
|---|---|---|
| JDK | 17+ | `G:/develop/jdk`（Temurin 21.0.11）；PATH 上的 `java` 是 JDK 18，构建时须显式指定 `JAVA_HOME` |
| Maven | 3.8+ | `G:/develop/apache-maven-3.9.16`，本地仓库 `G:/maven-repo` |
| MySQL | 8.0+ | 已建库 `tomshop`（8.0.44），root 密码走 `MYSQL_PASSWORD` 环境变量（`application-dev.yml` 带开发默认值） |
| Redis | 5.0+ | 默认 `localhost:6379`，无密码（Redis 5 的兼容处理见 [troubleshooting.md](docs/troubleshooting.md#31-redis-5-不支持-keepttl登录返回-5000)） |

### 2.1 初始化数据库（仅全新环境执行一次）

```bash
MYSQL="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
# 建库 + 建表（14 张）
"$MYSQL" -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
# 初始化数据（角色 / 菜单 / 权限 / 分类 / 示例商品 / 种子管理员）
"$MYSQL" -h 127.0.0.1 -u root -p --default-character-set=utf8mb4 tomshop < src/main/resources/db/data.sql
```

> ⚠️ `schema.sql` / `data.sql` 以 `DROP TABLE` 开头，**只能用于全新环境**。
> 已有数据的库一律走 `db/migration/` 增量脚本，详见
> [phase-1-database.md](docs/phase-1-database.md#四迁移脚本规范)。

### 2.2 构建与启动

```bash
# 显式指定 JDK 21，避免误用 PATH 上的 JDK 18
export JAVA_HOME="G:/develop/jdk"

mvn -q clean compile          # 编译
mvn -q test                   # 测试（44 个用例，需 MySQL + Redis 可用）
mvn spring-boot:run           # 启动（dev 环境）
```

启动后：

| 地址 | 说明 |
|---|---|
| `http://localhost:8080/api/v1/health` | 业务探活（应用信息 + MySQL/Redis 实时状态） |
| `http://localhost:8080/actuator/health` | Actuator 标准探活，供容器探针使用 |
| `http://localhost:8080/doc.html` | Knife4j 接口文档 |

**登录账号（仅开发环境）**：`admin / admin123`，拥有全部菜单与权限点。

```bash
# 登录 → 拿令牌 → 访问受保护接口
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer <accessToken>"
```

---

## 三、项目结构

```
Tomshop/
├── pom.xml
├── README.md
├── docs/                    分阶段设计文档 + 接口清单 + 踩坑记录
└── src/main/
    ├── java/com/tom/tomshop/
    │   ├── TomShopApplication.java      启动类（@MapperScan）
    │   ├── common/
    │   │   ├── base/         BaseEntity（审计字段+逻辑删除+乐观锁）、PageQuery
    │   │   ├── constant/     CommonConstants（全局）、AuthConstants / CartConstants / OrderConstants
    │   │   ├── context/      UserContext（当前用户 ThreadLocal，取值来源为 Sa-Token）
    │   │   ├── enums/        业务枚举（订单状态机在 OrderStatusEnum 内闭合）
    │   │   └── result/       Result、ResultCode、PageResult
    │   ├── config/
    │   │   ├── properties/   TomShopProperties（tomshop.* 配置绑定）
    │   │   ├── CorsConfig / JacksonConfig / Knife4jConfig
    │   │   ├── MybatisPlusConfig / MybatisMetaObjectHandler
    │   │   ├── RedisConfig       RedisTemplate 序列化
    │   │   ├── SaTokenConfig     登录校验 + 放行清单 + JWT 模式
    │   │   ├── SaTokenRedisConfig   Redis 5 兼容的会话存储（@Primary）
    │   │   ├── PasswordEncoderConfig  BCrypt
    │   │   ├── UserContextInterceptor 登录态 → UserContext（每个请求前后清理）
    │   │   └── WebMvcConfig      拦截器顺序：SaInterceptor(0) → UserContext(100)
    │   ├── security/
    │   │   ├── StpInterfaceImpl      权限/角色数据源（对接 sys_* 表）
    │   │   ├── RefreshTokenManager   refresh token 签发、一次性消费、批量吊销
    │   │   └── RedisCompatibleSaTokenDao
    │   ├── controller/   → service/ → mapper/
    │   │     认证 AuthController / 商品 SpuController / 分类 CategoryController
    │   │     购物车 CartController / 订单 OrderController / 健康 HealthController
    │   ├── dto/  vo/  convert/   MapStruct 转换器
    │   ├── exception/    BusinessException、GlobalExceptionHandler
    │   └── utils/        JsonUtils、PageUtils、OrderNoGenerator
    └── resources/
        ├── application.yml + application-dev.yml
        ├── logback-spring.xml
        ├── mapper/     自定义 ResultMap XML
        └── db/
            ├── schema.sql    建库 + 14 张表（全量重建，仅新环境用）
            ├── data.sql      初始化数据（全量重建，仅新环境用）
            └── migration/    增量迁移脚本（已有数据的库用这个）
```

**分层纪律**（全项目强制）：

1. `Controller` 只做「接参 → 校验 → 转发 → 包装返回值」。不注入 Mapper、不写业务分支、不加事务注解。
2. 业务逻辑全部在 `Service`，`ServiceImpl` 不出现 `HttpServletRequest`、`Result` 等 Web 类型。
3. 对外只返回 `VO`，绝不返回 `Entity` —— 后者会泄露表结构、逻辑删除标记与乐观锁版本号。
4. `Entity ↔ DTO ↔ VO` 的转换一律由 MapStruct 转换器承担，禁止手写逐字段 `set`。
5. Service 方法**显式接收 `userId`**，不在内部读 ThreadLocal：后者在异步线程、定时任务里会拿到 null。

---

## 四、编码约定

| 约束 | 说明 |
|---|---|
| 常量唯一来源 | 全局常量放 `CommonConstants`，模块常量放同包独立常量类。禁止在多处重复定义同一语义的值 |
| 禁止魔法值 | 状态、类型、标记一律用枚举或常量 |
| 排序字段 | `PageQuery.sortField` 拼入 `ORDER BY` 前**必须**经 `resolveSortField(白名单...)` 校验 |
| 敏感配置 | 密码、密钥走环境变量（`${ENV:default}`），不硬编码 |
| 日志 | 用 `@Slf4j`，占位符 `{}` 而非字符串拼接；异常日志带 uri + method |
| 参数校验 | Controller 入参一律 `@Validated` / `@Valid` |
| 日期格式 | 统一走 `CommonConstants` 中的 pattern 常量，禁止各处硬编码 |
| ID 与计数 | ID 一律包装类型 `Long`（序列化为字符串），计数一律基本类型 `long` |
| 响应与异常 | 统一 `Result<T>`；401/403 同时映射 HTTP 状态，其余业务码 HTTP 保持 200 |

---

## 五、开发进度

| 阶段 | 内容 | 状态 | 文档 |
|---|---|---|---|
| **0** | 项目初始化与基础骨架 | ✅ 已完成 | [phase-0-init.md](docs/phase-0-init.md) |
| **1** | 数据库设计与基础实体层 | ✅ 已完成 | [phase-1-database.md](docs/phase-1-database.md) |
| — | 重构：菜单 / 权限解耦 + `perm_id` 列名统一 | ✅ 已完成 | [phase-1-database.md](docs/phase-1-database.md#五阶段-1-后重构菜单与权限解耦2026-10-13) |
| **2** | 核心业务模块（商品 / 购物车 / 订单） | ✅ 已完成 | [phase-2-business.md](docs/phase-2-business.md) |
| **3** | 权限安全体系（Sa-Token + Redis + 双 JWT + 动态路由） | ✅ 已完成 | [phase-3-auth.md](docs/phase-3-auth.md) |
| 4 | 多级缓存与性能优化 | ⏳ | — |
| 5 | 并发、消息与在线客服 | ⏳ | — |
| 6 | 效率提升、文档与收尾 | ⏳ | — |

### 阶段 3 交付摘要

- **认证**：登录 / 注册 / 刷新 / 登出 / 当前用户 / 动态路由 6 个接口；
- **双令牌**：access token 2 小时（JWT + Redis 会话，可登出），refresh token 7 天（一次性轮换，复用即拒绝）；
- **权限**：`StpInterface` 对接 `sys_role_permission` / `sys_role_menu`，商品、分类写接口与订单发货已挂权限点；
- **路由**：`GET /auth/routes` 返回 vue-router 结构的路由树（目录 → `Layout` + `redirect`，路由名 PascalCase）；
- **匿名边界**：仅放行商品 / 分类的 **GET**，同路径写操作仍需登录 + 权限；
- **测试**：`AuthIntegrationTest` 15 个用例；全项目 44 个用例通过。

### 技术风险（待验证）

- **RocketMQ × Spring Boot 3**（阶段 5）：`rocketmq-spring-boot-starter 2.3.1` 的自动装配仍基于
  Spring Boot 2.x 的 `spring.factories` 机制，大概率需手工补 `AutoConfiguration.imports`。
  若不可行，退化为直接用 `rocketmq-client 5.x` 手写配置类。

---

## 六、文档2

| 文档 | 内容 |
|---|---|
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/phase-0-init.md](docs/phase-0-init.md) | 阶段 0：骨架、统一响应、异常、配置、探活 |
| [docs/phase-1-database.md](docs/phase-1-database.md) | 阶段 1：库表设计、实体层、CRUD 切片、迁移规范、菜单/权限解耦 |
| [docs/phase-2-business.md](docs/phase-2-business.md) | 阶段 2：商品 / 购物车 / 订单、库存与 upsert、状态机 |
| [docs/phase-3-auth.md](docs/phase-3-auth.md) | 阶段 3：Sa-Token、双 JWT、权限点、动态路由、鉴权测试 |
| [docs/api.md](docs/api.md) | 接口总览 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 跨阶段踩坑记录 |

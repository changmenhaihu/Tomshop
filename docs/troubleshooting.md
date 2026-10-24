# 踩坑记录（跨阶段）

按「现象 → 根因 → 处理」记录，便于同类问题快速定位。

## 一、构建环境

### 1.1 `该文件具有错误的版本 69.0，应为 65.0`

- **现象**：`mvn test-compile` 报某个 `target/classes` 下的类「版本 69.0，应为 65.0」，
  同时主源码编译又提示 `Nothing to compile - all classes are up to date`。
- **根因**：`target/classes` 里残留了由 **JDK 25**（major 69）编译的 class 文件，
  而 Maven 的增量编译只比较时间戳，不会重新生成它们；JDK 21 的 javac 读不了更高版本的 class。
- **处理**：`mvn clean` 后重新编译。**不要**在报这类版本错误时反复改 POM。
- **预防**：执行构建前显式 `export JAVA_HOME="G:/develop/jdk"`（PATH 上的 `java` 是 JDK 18，
  IDE 内置 JRE 可能是更新的版本）。

### 1.2 MapStruct 的 `Unmapped target property` 警告

- 属于提示性警告，常用于发现 `children` 等需要手工组装的字段；确认无需映射即可忽略。

## 二、依赖与版本

### 2.1 MyBatis-Plus 3.5.9 拆包

自 3.5.9 起 jsqlparser 被移出核心包改为可选依赖：

| 类 | 所在包 |
|---|---|
| `PaginationInnerInterceptor`（分页） | `mybatis-plus-jsqlparser`（需显式引入） |
| `BlockAttackInnerInterceptor`（防全表更新） | `mybatis-plus-jsqlparser`（需显式引入） |
| `OptimisticLockerInnerInterceptor` | 核心包，仍然可用 |

- **症状**：编译期「找不到符号」，且**只报分页与防全表更新**，乐观锁正常 ——
  这个「报一半」的现象很容易被误判为依赖版本写错。
- **处理**：POM 已显式引入 `mybatis-plus-jsqlparser` 适配包。

### 2.2 Knife4j × Spring Boot 3.3

knife4j 4.5.0 传递的 springdoc 2.3.0 面向 Spring Boot 3.2。已在 POM 中显式提升到 2.6.0。
实测 `/doc.html`、`/v3/api-docs` 返回 200。

### 2.3 Sa-Token 的 `sa-token-redis-jackson` 是空壳

- `sa-token-redis-jackson-1.46.0.jar` 只是聚合器，实际实现（`SaTokenDaoForRedisTemplate`）
  在 `sa-token-redis-template` 中，经 `AutoConfiguration.imports` 装配。
- 需要调整 DAO 时，继承 `SaTokenDaoForRedisTemplate` 并用 `@Primary` 抢占注入点即可。

## 三、中间件

### 3.1 Redis 5 不支持 `KEEPTTL`，登录返回 5000

- **现象**：密码正确却返回 `code: 5000 系统繁忙`，日志里有
  `RedisCommandExecutionException: ERR syntax error`。
- **根因**：`SaTokenDaoForRedisTemplate.setStringAndKeepTTL` 使用 `SET ... KEEPTTL`，
  该语法是 **Redis 6.0** 才引入的；本机是 Redis 5.0.14（Windows 版长期停在 5.x）。
- **处理**：`RedisCompatibleSaTokenDao` 改为「先 `TTL` 再 `SET EX`」，注册为 `@Primary SaTokenDao`。
  语义等价（读改写非原子，但会话值本就是后写覆盖前写，TTL 只会略微提前）。
- **升级路径**：Redis 升到 6.0+ 后，删除 `SaTokenRedisConfig` 与 `RedisCompatibleSaTokenDao` 即可。

### 3.2 JDBC `characterEncoding=utf8mb4` 非法

- MySQL Connector/J 的 `characterEncoding` 只认 **Java 字符集名**，应写 `UTF-8`
  （Connector/J 8.x 会自动映射到 utf8mb4）；写 `utf8mb4` 会抛
  `Unsupported character encoding 'utf8mb4'`，进而 `CannotGetJdbcConnectionException`。
- `utf8mb4` 是 mysql 客户端/服务器端的叫法，不是 JDBC 参数合法值。

## 四、数据

### 4.1 对已有库执行了 `schema.sql`

`schema.sql` / `data.sql` 以 `DROP TABLE` 开头，是全量重建脚本。已有真实数据的库执行它们会清空业务数据。
已有库一律使用 `db/migration/` 下的增量脚本。

### 4.2 逻辑删除行占用唯一键

`@TableLogic` 只影响 MyBatis-Plus 生成的条件；`uk_user_sku(user_id, sku_id)` 不含 `deleted`，
被逻辑删除的购物车条目仍占用唯一键。加购必须用 `INSERT ... ON DUPLICATE KEY UPDATE`，
不能「先查后插」。详见 [phase-2-business.md](phase-2-business.md#34-加购的-upsert为什么不能先查后插)。

## 五、安全

### 5.1 JWT 密钥默认值

`sa-token.jwt-secret-key` 的默认值仅供开发。生产必须通过环境变量 `SA_JWT_SECRET` 覆盖；
密钥短于 32 字节时 HS256 签名会直接报错。

### 5.2 种子管理员密码

`admin / admin123` 仅用于开发环境联调，上线前必须修改（或删除该账号）。

# 阶段 3：权限安全体系（Sa-Token + 双 JWT）

> 目标：把阶段 2 里「客户端可以随便伪造身份」的临时方案换成真正的鉴权，
> 并给出前端可直接注册的动态路由树。**本阶段不做角色/用户管理接口**，那是管理端的业务功能，随阶段 4/6 补。

## 一、为什么是 Sa-Token

原计划 Shiro 2.0.2，已弃用。Sa-Token 原生覆盖「双 Token + 无感续期 + 账号封禁」这套需求，
内置 `StpInterface` 回调可直接对接 `sys_role_permission`，不需要像 Shiro 那样自行拼装
Realm / FilterChain / 自定义 Token 解析器。

> Spring Boot 3 必须用 `cn.dev33:sa-token-spring-boot3-starter`，用错成
> `sa-token-spring-boot-starter` 会因 `javax.servlet` 无法装配。

## 二、依赖与配置

| 依赖 | 作用 |
|---|---|
| `sa-token-spring-boot3-starter` | 核心 + Web 拦截 |
| `sa-token-jwt` | JWT 模式（传递 hutool-jwt） |
| `sa-token-redis-jackson` | 会话持久化到 Redis |
| `spring-security-crypto` | 只要 `BCryptPasswordEncoder`，**不引** `spring-boot-starter-security`（会带来整套过滤器链，与 Sa-Token 打架） |

`application.yml`：

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer        # 请求头：Authorization: Bearer <token>
  timeout: 7200               # access token 2 小时
  active-timeout: -1          # 关闭滑动续期，续期交给 refresh token
  is-concurrent: true         # 允许多端同时在线（手机 + 平板）
  is-share: false             # 每次登录签发独立令牌
  is-read-header: true
  is-read-cookie: false       # 只走请求头，不引入 CSRF 面
  jwt-secret-key: ${SA_JWT_SECRET:tomshop-dev-only-jwt-secret-key-change-in-prod}
```

`tomshop.auth.*` 配置 refresh token 时长与前缀（默认 7 天 / `tomshop:auth:refresh:`）。

## 三、双令牌设计

### 3.1 为什么要两个令牌

access token 是 JWT，**签发后在过期前无法收回**。缩短时效是限制「令牌被盗」危害窗口的唯一手段，
但只有短令牌意味着用户每两小时重输一次密码。于是补一个长时效 refresh token 专门换新 access token。

### 3.2 两者的分工

| | access token | refresh token |
|---|---|---|
| 实现 | Sa-Token 会话 + JWT（`StpLogicJwtForSimple`） | 原生 JWT（`SaJwtUtil`）+ Redis 登记 |
| 有效期 | 2 小时 | 7 天 |
| 用途 | 所有业务接口 | 只能调 `POST /auth/refresh` |
| 可吊销 | 是（登出销毁 Redis 会话） | 是（Redis 中按 `jti` 删除） |
| 一次性 | 否 | **是，刷新即轮换** |

`StpLogicJwtForSimple` 的要点：JWT 负责「不可伪造」，Redis 会话负责「可吊销」。
纯 JWT 无状态方案无法登出，纯 Redis 方案又要每次请求读库，Simple 模式取两者交集。

### 3.3 refresh token 的轮换与泄露检测

```
tomshop:auth:refresh:{jti}         -> userId     单令牌，用于一次性消费
tomshop:auth:refresh:uid:{userId}  -> Set<jti>   用户索引，用于登出批量吊销
```

- 只存 `jti` 不存整串 JWT：令牌由签名保证不可篡改，库里存它没有意义，
  反而会把敏感串散落到备份与慢查询日志里；
- 刷新的第 1 步是 `DEL {jti}`，**返回 0 说明这个令牌已经被用过** —— 直接 401。
  这是发现令牌泄露的主要手段：攻击者用过的令牌，合法用户再用就会失败；
- `typ=refresh` 声明让 access token 无法被拿到刷新接口使用（反之亦然）；
- 两个令牌共用同一把密钥：本属同一信任域，共用不会扩大攻击面，`typ` 声明负责隔离用途。

### 3.4 已知限制

**旧 access token 在刷新后仍然有效**，直到 2 小时自然过期。JWT 无状态，无法主动收回单个令牌。
缓解手段是短 TTL + 登出时销毁 Redis 会话；要做到「刷新即吊销旧 access token」，
必须为每个 access token 在 Redis 登记 `jti`，那会抵消 JWT 的无状态收益。当前版本接受此限制。

## 四、权限数据源与被拦截范围

### 4.1 `StpInterfaceImpl`

Sa-Token 收到 `@SaCheckPermission("product:spu:save")` 时会回调本类：

| 方法 | SQL 来源 |
|---|---|
| `getPermissionList` | `sys_user_role → sys_role → sys_role_permission → sys_permission`，取 `DISTINCT p.perms`，过滤 `deleted=0 / status=1` |
| `getRoleList` | `sys_user_role → sys_role`，取 `DISTINCT r.role_code` |

⚠️ `loginId` 参数是 `Object`，经 JSON / Redis 往返后可能是 `Integer` / `Long` / `String`，
**不能直接强转**，统一走 `parseLoginId(Object)`。

### 4.2 放行清单（`SaTokenConfig`）

| 类别 | 路径 |
|---|---|
| 认证 | `/api/v1/auth/login`、`/register`、`/refresh` |
| 运维 | `/api/v1/health`、`/actuator/**` |
| 文档 | `/doc.html`、`/webjars/**`、`/v3/api-docs/**`、`/swagger-ui/**` |
| 其他 | `/favicon.ico`、`/error` |
| **只读匿名** | `GET /api/v1/products/**`、`GET /api/v1/categories/**` |

最后一行是最容易配错的地方：**只放行 GET**。同一路径的 `POST /products` 必须登录 + 有权限。
实现上用 `SaRouter.isMatchCurrMethod(GET) && isMatchCurrURI(...)` 双重判断
（Sa-Token 的 `notMatch` 没有方法维度的重载）。

`SaInterceptor` 拦截 `/**`，order = 0；`UserContextInterceptor` order = 100，
保证它执行时登录态已经校验完毕。

### 4.3 已挂权限点的接口

| 接口 | 权限点 |
|---|---|
| `POST/PUT/DELETE /products[/{id}]` | `product:spu:save` |
| `PUT /products/{id}/status` | `product:spu:status` |
| `POST/PUT/DELETE /categories[/{id}]` | `product:category:save` |
| `PUT /orders/{id}/deliver` | `order:info:deliver` |

## 五、动态路由

`GET /api/v1/auth/routes` 返回当前用户可访问的路由树，字段与 vue-router 对齐：

```json
[{
  "path": "/system",
  "name": "System",
  "component": "Layout",
  "redirect": "/system/user",
  "meta": { "title": "系统管理", "icon": "setting", "hidden": false, "sort": 1 },
  "children": [
    { "path": "/system/user", "name": "SystemUser", "component": "system/user/index",
      "meta": { "title": "用户管理", "hidden": false, "sort": 1 } }
  ]
}]
```

组装规则：

| 规则 | 说明 |
|---|---|
| 数据源 | `sys_menu` 经 `sys_role_menu` / `sys_user_role` 过滤，`ORDER BY sort, id` |
| **不过滤 `visible`** | 隐藏菜单仍然需要路由（如详情页），隐藏只由 `meta.hidden` 表达 |
| 目录 → `Layout` | `type=1` 的节点 `component` 固定为 `Layout`，作为路由容器 |
| 目录必须有 `redirect` | 取第一个子节点，否则前端访问 `/system` 会渲染空布局 |
| `name` 唯一 | 由 `path` 按 `[/\-_.]+` 切分后 PascalCase（`/system/user` → `SystemUser`），冲突时追加序号 |
| 孤儿节点上提 | 父节点不可见/已被删但子节点仍授权时，提升为根节点并记 warn，而不是静默丢弃 |

## 六、身份上下文的切换

`UserContextInterceptor` 从「读 `X-User-Id` 请求头」改为：

```java
if (StpUtil.isLogin()) {
    UserContext.setUserId(StpUtil.getLoginIdAsLong());
}
```

`afterCompletion` 中清除 —— Tomcat 线程复用，不清会让下一个请求读到上一个用户。

**Controller / Service 一行未改**：阶段 2 定下的「Service 显式接收 `userId` 参数」在这里兑现了收益。
`X-User-Id` 请求头已彻底移除。

## 七、异常与 HTTP 状态的对应关系

阶段 0 的原则是「HTTP 恒 200，业务码驱动行为」，阶段 3 对此做了一处**刻意的例外**：

| 场景 | HTTP | 业务码 |
|---|---|---|
| 未登录 / 令牌过期 / 伪造 | **401** | 401 |
| 缺权限点 / 缺角色 | **403** | 403 |
| 其他业务异常 | 200 | 对应业务码 |

原因：401/403 是**协议层**语义，前端 axios 拦截器需要据此触发「清 token → 跳登录」，
让它们以 200 返回会把「未登录」混进正常业务分支。

`NotLoginException` 按 `type` 给出中文提示（过期 / 被顶下线 / 踢下线 / 冻结 / 缺前缀 / 其他），
不把 Sa-Token 的原始英文异常直接抛给前端。

## 八、种子管理员

`db/migration/2026-10-24__seed_admin_user.sql`：

- 账号 `admin` / 初始密码 `admin123`（BCrypt 存储，**仅开发环境，上线前必须修改**）；
- 绑定 `ADMIN` 角色，当前拥有 3 个根菜单、10 个菜单节点、18 个权限点；
- 脚本带前置校验（表与 ADMIN 角色存在）且用 `INSERT ... SELECT ... WHERE NOT EXISTS` 保证幂等。

`data.sql` 也同步了同样的种子数据，供全新环境使用。

## 九、测试

`AuthIntegrationTest`（15 个用例，MockMvc 全链路，`@Transactional` 回滚）：

- 登录成功返回双令牌 + 角色 + 权限；密码错误 401；**账号不存在与密码错误的提示完全一致**（防账号枚举）；
- 匿名可浏览商品/分类；匿名访问购物车 401；匿名 `POST /products` 401（同路径只放行 GET）；
- 缺 `Bearer` 前缀、伪造令牌均 401；
- `/auth/me` 返回 18 个权限点且不含 `password`；
- 管理员路由树：3 个根节点、`/system` 是 `Layout` + `redirect=/system/user`、子路由名为 PascalCase；
- 普通用户（自动注册）路由与权限均为空，访问管理端接口 403；
- refresh 轮换：新令牌可用、旧 refresh 复用被拒、access token 不能当 refresh 用；
- 登出后 access 与 refresh 同时失效。

总计项目测试 **44 个用例全部通过**。

## 十、已知取舍

1. **Redis 5 兼容实现**：`sa-token-redis-template` 的 `setStringAndKeepTTL` 用了 Redis 6.0 才有的
   `KEEPTTL`，本机 Redis 5.0.14 会抛 `ERR syntax error`（表现为「密码正确却返回 5000」）。
   已用 `RedisCompatibleSaTokenDao`（先 `TTL` 再 `SET EX`）覆盖，见 [troubleshooting.md](troubleshooting.md)。
2. **登出只销毁当前会话**（`is-concurrent=true` + `is-share=false`）：其他设备的登录态不受影响，
   这是多端在线的预期行为；refresh token 则按用户全部吊销。
3. **JWT 密钥默认值仅供开发**，生产必须通过 `SA_JWT_SECRET` 覆盖；密钥短于 32 字节时 HS256 签名会直接报错。

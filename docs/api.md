# 接口总览

- 统一前缀：`/api/v1`
- 统一响应：`Result<T>`，`code = 200` 为成功；401/403 同时体现在 HTTP 状态码上
- 鉴权：`Authorization: Bearer <accessToken>`
- 在线文档：`GET /doc.html`（Knife4j）

图例：🔓 匿名可访问　🔐 需登录　🛡 需登录 + 指定权限点

## 认证

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/auth/login` | 🔓 | 登录，返回 access（2h）+ refresh（7d）双令牌 |
| POST | `/auth/register` | 🔓 | 自助注册，自动绑定 `USER` 角色并返回令牌 |
| POST | `/auth/refresh` | 🔓 | 用 refresh token 换新令牌，旧的立即失效 |
| POST | `/auth/logout` | 🔐 | 销毁当前会话并吊销该用户全部 refresh token |
| GET | `/auth/me` | 🔐 | 当前用户信息 + 角色 + 权限点 |
| GET | `/auth/routes` | 🔐 | 当前用户的动态路由树 |

## 健康检查

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/health` | 🔓 | 业务探活（应用信息 + MySQL/Redis 实时状态） |

## 分类

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/categories/tree` | 🔓 | 分类树 |
| GET | `/categories` | 🔓 | 分页列表 |
| GET | `/categories/{id}` | 🔓 | 详情 |
| POST | `/categories` | 🛡 `product:category:save` | 新增 |
| PUT | `/categories/{id}` | 🛡 `product:category:save` | 更新 |
| DELETE | `/categories/{id}` | 🛡 `product:category:save` | 逻辑删除 |

## 商品

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/products` | 🔓 | 分页（分类含子孙、关键字、价格区间、排序白名单） |
| GET | `/products/{id}` | 🔓 | 详情（含 SKU 列表） |
| POST | `/products` | 🛡 `product:spu:save` | 新增（至少一个 SKU，展示价取 SKU 最低价） |
| PUT | `/products/{id}` | 🛡 `product:spu:save` | 更新（SKU 差异同步） |
| PUT | `/products/{id}/status` | 🛡 `product:spu:status` | 上下架 |
| DELETE | `/products/{id}` | 🛡 `product:spu:save` | 逻辑删除（SKU 一并删除） |

## 购物车

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/cart` | 🔐 | 购物车（含失效标记与勾选合计） |
| POST | `/cart` | 🔐 | 加购（同 SKU 累加） |
| PUT | `/cart/items/{itemId}` | 🔐 | 改量（绝对值） |
| DELETE | `/cart/items?itemIds=` | 🔐 | 批量删除 |
| PUT | `/cart/selected` | 🔐 | 勾选 / 全选（`itemIds` 为空表示全量） |

## 订单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/orders` | 🔐 | 提交订单（`source` 1 购物车 / 2 立即购买） |
| GET | `/orders/preview?addressId=` | 🔐 | 下单前预览（运费与应付金额，只读） |
| GET | `/orders` | 🔐 | 我的订单分页 |
| GET | `/orders/{id}` | 🔐 | 详情（含明细快照与 `canCancel/canDeliver/canFinish`） |
| PUT | `/orders/{id}/cancel` | 🔐 | 取消（回滚库存） |
| PUT | `/orders/{id}/finish` | 🔐 | 确认收货 |
| PUT | `/orders/{id}/deliver` | 🛡 `order:info:deliver` | 发货（管理端） |

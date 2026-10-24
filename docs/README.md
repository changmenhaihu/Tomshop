# TomShop 文档索引

项目的设计说明按**阶段**拆分，每个阶段一份独立文档。新增阶段时只新增一份 `phase-N-*.md`，
不要在已有文档里续写 —— 这样每份文档都只描述「该阶段做了什么、为什么这么做」。

| 文档 | 内容 |
|---|---|
| [phase-0-init.md](phase-0-init.md) | 阶段 0：项目骨架、统一响应、异常处理、基础配置、健康探活 |
| [phase-1-database.md](phase-1-database.md) | 阶段 1：数据库设计、实体与 Mapper、CRUD 垂直切片、菜单/权限解耦重构 |
| [phase-2-business.md](phase-2-business.md) | 阶段 2：商品 / 购物车 / 订单核心业务 |
| [phase-3-auth.md](phase-3-auth.md) | 阶段 3：Sa-Token + 双 JWT 鉴权、权限点、动态路由 |
| [api.md](api.md) | 接口总览（持续更新） |
| [troubleshooting.md](troubleshooting.md) | 跨阶段踩坑记录（依赖版本、驱动、中间件） |

返回 [项目主页](../README.md)。

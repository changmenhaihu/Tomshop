package com.tom.tomshop.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局业务状态码。
 *
 * <p>编码规范（后续阶段新增状态码必须遵守，避免撞码）：
 * <ul>
 *   <li>2xx / 4xx ：与 HTTP 语义对齐的通用状态</li>
 *   <li>1xxx      ：用户与权限模块</li>
 *   <li>2xxx      ：商品与分类模块</li>
 *   <li>3xxx      ：购物车模块</li>
 *   <li>4xxx      ：订单模块</li>
 *   <li>5xxx      ：系统级错误</li>
 * </ul>
 *
 * @author Tom
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    /** 参数校验失败（@Validated 触发） */
    PARAM_INVALID(400, "参数校验失败"),
    /** 未登录或 Token 失效，前端应触发刷新 Token 或跳转登录 */
    UNAUTHORIZED(401, "未登录或登录状态已过期"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不被支持"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    /** 通用业务失败 */
    FAILED(500, "操作失败"),

    /** 系统级异常兜底 */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试");

    /** 业务状态码 */
    private final Integer code;

    /** 默认提示信息 */
    private final String message;
}

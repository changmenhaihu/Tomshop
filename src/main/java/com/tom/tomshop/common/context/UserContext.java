package com.tom.tomshop.common.context;

import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.exception.BusinessException;

/**
 * 当前登录用户的线程上下文。
 *
 * <p><b>身份来源的抽象边界</b>：购物车、订单这些模块只需要知道「当前用户是谁」，
 * 不关心令牌长什么样。所以取数逻辑统一收敛在
 * {@link com.tom.tomshop.config.UserContextInterceptor}：阶段 2 从请求头 {@code X-User-Id}
 * 临时读取，阶段 3 改为 {@code StpUtil.getLoginIdAsLong()}。
 * <b>换实现时 Service 层一行都没改</b> —— 这正是当初把这个边界单独抽出来的目的。
 *
 * <p><b>与 Sa-Token 的关系</b>：本类只是登录态的一份「线程内快照」，不是鉴权决策点。
 * 谁能不能访问某个接口由 {@code Sa-Token} 的拦截器与权限注解决定；本类只负责
 * 把已经通过校验的用户 ID 传给业务方法。
 *
 * <p><b>为什么用 ThreadLocal</b>：业务方法签名不必层层传递 userId（Controller → Service →
 * 校验 → 落库），避免把 Web 层概念污染进 Service。
 *
 * <p><b>务必清理</b>：Tomcat 复用线程池，若请求结束后不清理，<b>下一个请求会读到上一个用户的身份</b>。
 * 这是真实的安全事故（A 用户的购物车被 B 用户看到），所以清理动作放在拦截器的
 * {@code afterCompletion} 中执行 —— 无论请求成功还是抛异常都会走到。
 *
 * @author Tom
 */
public final class UserContext {

    /** 当前用户 ID；未登录时为 null，而非 0 —— 0 是可达的业务值，用它表示「无」会造成歧义 */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前用户 ID，仅供拦截器调用。
     */
    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 未登录返回 {@code null}
     */
    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 获取当前用户 ID，未登录直接抛 401。
     *
     * <p>需要登录态的业务（购物车、订单）一律用本方法，把「未登录」的判定收敛到一处，
     * 避免每个方法各写一遍 {@code if (userId == null) throw ...}。
     *
     * @return 当前用户 ID，不为 null
     * @throws BusinessException 未登录时抛出，业务码 401
     */
    public static Long requireUserId() {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录后再操作");
        }
        return userId;
    }

    /**
     * 清理当前线程的用户身份。必须由拦截器在请求结束时调用。
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}

package com.tom.tomshop.security;

import cn.dev33.satoken.stp.StpInterface;
import com.tom.tomshop.mapper.SysPermissionMapper;
import com.tom.tomshop.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 的权限/角色数据源。
 *
 * <p>Sa-Token 收到 {@code @SaCheckPermission("product:spu:save")} 时，会回调本类的
 * {@link #getPermissionList} 拿到当前账号的权限集合再做比对。<b>每次鉴权都会查一次库</b>——
 * 阶段 3 有意接受这个开销（两条带索引的 JOIN，毫秒级），换取「管理员改了权限立即生效」。
 * 阶段 4 引入 Caffeine + Redis 多级缓存后，这里会改成先读缓存、失效时回源，
 * 届时只需替换本类实现，注解与业务代码不动。
 *
 * <p>登录 ID 一律以参数 {@code loginId} 为准，而不是在方法内调用
 * {@code StpUtil.getLoginIdAsLong()}：Sa-Token 可能在非请求线程（如定时任务、异步线程）
 * 中回调本接口，那时请求上下文已不存在，取全局登录态会抛异常。
 *
 * @author Tom
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = parseLoginId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> perms = permissionMapper.selectPermsByUserId(userId);
        return perms == null ? Collections.emptyList() : perms;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = parseLoginId(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        return roles == null ? Collections.emptyList() : roles;
    }

    /**
     * 把 Sa-Token 传来的登录 ID 统一成 Long。
     *
     * <p>不能直接强转 {@code (Long)}：登录态经 Redis 与 JWT 两次序列化往返后，
     * 同一数值可能回来的是 {@code Integer}（小数值时）或字符串，直接强转会抛
     * {@code ClassCastException}，而那个异常发生在鉴权内部，报错信息完全看不出根因。
     */
    private static Long parseLoginId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(loginId.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("[权限] 非法登录 ID，已按无权限处理：{}", loginId);
            return null;
        }
    }
}

package com.tom.tomshop.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tom.tomshop.common.constant.AuthConstants;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.dto.LoginDTO;
import com.tom.tomshop.dto.RefreshTokenDTO;
import com.tom.tomshop.dto.RegisterDTO;
import com.tom.tomshop.entity.SysMenu;
import com.tom.tomshop.entity.SysRole;
import com.tom.tomshop.entity.SysUser;
import com.tom.tomshop.entity.SysUserRole;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.SysMenuMapper;
import com.tom.tomshop.mapper.SysPermissionMapper;
import com.tom.tomshop.mapper.SysRoleMapper;
import com.tom.tomshop.mapper.SysUserMapper;
import com.tom.tomshop.mapper.SysUserRoleMapper;
import com.tom.tomshop.security.RefreshTokenManager;
import com.tom.tomshop.service.AuthService;
import com.tom.tomshop.vo.LoginVO;
import com.tom.tomshop.vo.RouteMetaVO;
import com.tom.tomshop.vo.RouteVO;
import com.tom.tomshop.vo.UserInfoVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 认证服务实现。
 *
 * <p><b>双令牌方案</b>：
 * <pre>
 *   access token  (JWT, 2h)   -> 每次业务请求的 Authorization 头，Sa-Token 直接校验
 *   refresh token (JWT, 7d)   -> 只用于 /auth/refresh，一次性消费并轮换
 * </pre>
 * access token 短、refresh token 长且可吊销，二者配合既限制了令牌泄露的危害窗口，
 * 又不会让用户频繁重新登录。详见 {@link RefreshTokenManager}。
 *
 * <p><b>为什么登录不抛「用户不存在」</b>：账号枚举。若「用户不存在」和「密码错误」
 * 给出不同提示，攻击者可以先用注册接口或登录接口把有效账号名刷出来，再针对性爆破。
 * 这里两种分支统一提示，并且用户不存在时也执行一次 BCrypt 比对，使响应耗时接近。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenManager refreshTokenManager;

    /**
     * 用户不存在时用来「空转」一次 BCrypt 的哈希。
     *
     * <p>启动时现算，而不是硬编码一个常量：常量哈希一旦被误改（比如有人以为是测试数据删掉），
     * 等时逻辑会静默失效，而且不会有任何报错。
     */
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyPasswordHash() {
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // ==================== 登录 / 注册 ====================

    @Override
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));

        if (user == null) {
            // 等时处理：让「账号不存在」与「密码错误」的耗时大致相同
            passwordEncoder.matches(dto.getPassword(), dummyPasswordHash);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!Objects.equals(AuthConstants.USER_STATUS_ENABLED, user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        StpUtil.login(user.getId());
        touchLastLoginTime(user);
        log.info("[认证] 登录成功。userId={}, username={}", user.getId(), user.getUsername());
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname().trim() : username);
        user.setPhone(StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : null);
        user.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : null);
        user.setGender(0);
        user.setStatus(AuthConstants.USER_STATUS_ENABLED);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 依赖数据库唯一索引而不是「先 select 再 insert」：后者在并发注册下必定漏网
            throw new BusinessException(ResultCode.FAILED, "用户名已被占用");
        }

        assignDefaultRole(user.getId());
        StpUtil.login(user.getId());
        log.info("[认证] 注册成功。userId={}, username={}", user.getId(), username);
        return buildLoginVO(user);
    }

    // ==================== 令牌刷新 / 登出 ====================

    @Override
    public LoginVO refresh(RefreshTokenDTO dto) {
        Long userId = refreshTokenManager.consume(dto.getRefreshToken());
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (!Objects.equals(AuthConstants.USER_STATUS_ENABLED, user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 旧 access token 不会被主动销毁：JWT 一旦签发在过期前无法收回，
        // 它最长也只剩下 2 小时寿命。这也是 access token 必须短的原因。
        StpUtil.login(user.getId());
        log.info("[认证] 令牌刷新成功。userId={}", userId);
        return buildLoginVO(user);
    }

    @Override
    public void logout() {
        Long userId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        refreshTokenManager.revokeAll(userId);
        log.info("[认证] 登出成功。userId={}", userId);
    }

    // ==================== 当前用户 / 动态路由 ====================

    @Override
    public UserInfoVO currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(AuthConstants.USER_STATUS_ENABLED, user.getStatus())) {
            // 账号在登录期间被删除或封禁：主动销毁会话，避免前端继续拿着有效令牌打接口
            StpUtil.logout();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return buildUserInfo(user);
    }

    @Override
    public List<RouteVO> routes() {
        Long userId = StpUtil.getLoginIdAsLong();
        return buildRouteTree(menuMapper.selectMenusByUserId(userId));
    }

    // ==================== 内部方法 ====================

    private LoginVO buildLoginVO(SysUser user) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return LoginVO.builder()
                .accessToken(tokenInfo.getTokenValue())
                .refreshToken(refreshTokenManager.issue(user.getId()))
                .tokenType(SaManager.getConfig().getTokenPrefix())
                // 直接取运行时剩余有效期，而不是读配置：Sa-Token 可能在登录时按策略调整有效期
                .expiresIn(StpUtil.getTokenTimeout())
                .user(buildUserInfo(user))
                .build();
    }

    private UserInfoVO buildUserInfo(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .gender(user.getGender())
                .lastLoginTime(user.getLastLoginTime())
                .roles(roleMapper.selectRoleCodesByUserId(user.getId()))
                .permissions(permissionMapper.selectPermsByUserId(user.getId()))
                .build();
    }

    private void touchLastLoginTime(SysUser user) {
        LocalDateTime now = LocalDateTime.now();
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setLastLoginTime(now);
        // update-strategy=not_null：只更新 last_login_time 这一列，不会把密码等字段覆盖成 null
        userMapper.updateById(patch);
        user.setLastLoginTime(now);
    }

    private void assignDefaultRole(Long userId) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, AuthConstants.DEFAULT_ROLE_CODE)
                .last("LIMIT 1"));
        if (role == null) {
            // 不阻断注册：账号本身是有效的，只是暂时没有角色，后台补齐关联即可
            log.warn("[认证] 默认角色 {} 不存在，注册用户 {} 未分配角色",
                    AuthConstants.DEFAULT_ROLE_CODE, userId);
            return;
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);
    }

    /**
     * 把扁平的菜单列表组装成路由树。
     *
     * <p><b>排序</b>：SQL 已按 {@code sort, id} 排好，这里按原顺序建节点、按原顺序挂子节点，
     * 因此同级顺序与后台配置一致，不需要再排一次。
     *
     * <p><b>孤儿节点</b>：如果某个子菜单被授权、但它的父目录没有被授权（典型的配置失误），
     * 这里把它<b>提升为一级路由</b>并打 warn，而不是静默丢弃 —— 静默丢弃会让管理员对着
     * 「权限明明给了却看不到菜单」排查半天。提升后至少能访问，问题在日志里可见。
     */
    private List<RouteVO> buildRouteTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, MenuNode> nodes = new LinkedHashMap<>(menus.size());
        for (SysMenu menu : menus) {
            nodes.put(menu.getId(), new MenuNode(menu));
        }

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes.values()) {
            Long parentId = node.menu.getParentId();
            boolean isRoot = parentId == null || parentId == 0L;
            MenuNode parent = isRoot ? null : nodes.get(parentId);
            if (parent != null) {
                parent.children.add(node);
                continue;
            }
            if (!isRoot) {
                log.warn("[动态路由] 菜单 {}「{}」的父菜单 {} 未授权，已提升为一级路由",
                        node.menu.getId(), node.menu.getName(), parentId);
            }
            roots.add(node);
        }

        Set<String> usedNames = new HashSet<>(menus.size() * 2);
        List<RouteVO> result = new ArrayList<>(roots.size());
        for (MenuNode root : roots) {
            result.add(toRouteVO(root, usedNames));
        }
        return result;
    }

    private RouteVO toRouteVO(MenuNode node, Set<String> usedNames) {
        SysMenu menu = node.menu;
        // 先登记自身名称，再处理子节点，保证「父路由名优先占用」的直觉顺序
        String name = uniqueRouteName(menu.getPath(), usedNames);

        List<RouteVO> children = new ArrayList<>(node.children.size());
        for (MenuNode child : node.children) {
            children.add(toRouteVO(child, usedNames));
        }

        boolean directory = Objects.equals(AuthConstants.MENU_TYPE_DIRECTORY, menu.getMenuType());
        return RouteVO.builder()
                .path(menu.getPath())
                .name(name)
                .component(directory ? AuthConstants.LAYOUT_COMPONENT : menu.getComponent())
                // 目录没有重定向的话，前端访问 /system 会渲染出一个空壳布局
                .redirect(directory && !children.isEmpty() ? children.get(0).getPath() : null)
                .meta(RouteMetaVO.builder()
                        .title(menu.getName())
                        .icon(menu.getIcon())
                        .hidden(!Objects.equals(AuthConstants.MENU_VISIBLE_SHOW, menu.getVisible()))
                        .sort(menu.getSort())
                        .build())
                .children(children)
                .build();
    }

    /**
     * 由路径推导路由名，并保证全局唯一。
     *
     * <p>{@code /system/user → SystemUser}。不同目录下出现同名末级路径时（如
     * {@code /product/list} 与 {@code /order/list}），第二个会得到 {@code OrderList}，
     * 若仍冲突则追加序号，避免 vue-router 因重名覆盖前一条路由。
     */
    private static String uniqueRouteName(String path, Set<String> usedNames) {
        String base = toRouteName(path);
        String candidate = base;
        int suffix = 1;
        while (!usedNames.add(candidate)) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    private static String toRouteName(String path) {
        if (!StringUtils.hasText(path)) {
            return "Route";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("[/\\-_.]+")) {
            if (segment.isBlank()) {
                continue;
            }
            sb.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
        }
        return sb.length() == 0 ? "Route" : sb.toString();
    }

    /** 组装树期间的临时节点：只持有菜单与子节点引用，不对外暴露 */
    private static final class MenuNode {

        private final SysMenu menu;
        private final List<MenuNode> children = new ArrayList<>();

        private MenuNode(SysMenu menu) {
            this.menu = menu;
        }
    }
}

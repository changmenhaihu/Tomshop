package com.tom.tomshop.service;

import com.tom.tomshop.dto.LoginDTO;
import com.tom.tomshop.dto.RefreshTokenDTO;
import com.tom.tomshop.dto.RegisterDTO;
import com.tom.tomshop.vo.LoginVO;
import com.tom.tomshop.vo.RouteVO;
import com.tom.tomshop.vo.UserInfoVO;

import java.util.List;

/**
 * 认证与授权服务。
 *
 * <p>职责：登录、注册、令牌刷新、登出、当前用户信息、动态路由。
 * 用户、角色、菜单的管理（增删改）属于阶段 3 之后的系统管理模块，不在此接口内。
 *
 * @author Tom
 */
public interface AuthService {

    /**
     * 账号密码登录，成功后签发 access token 与 refresh token。
     *
     * @throws com.tom.tomshop.exception.BusinessException 账号或密码错误（401）、账号被禁用（403）
     */
    LoginVO login(LoginDTO dto);

    /**
     * B2C 用户注册并自动登录。注册账号固定绑定 {@code USER} 角色。
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 用 refresh token 换取新的令牌对。旧 refresh token 在本次调用后作废（轮换）。
     *
     * @throws com.tom.tomshop.exception.BusinessException 令牌过期、被篡改、类型不符或已被使用（401）
     */
    LoginVO refresh(RefreshTokenDTO dto);

    /**
     * 登出：销毁 access token 会话，并吊销该用户全部 refresh token。
     */
    void logout();

    /**
     * 当前登录用户的信息、角色与权限列表。
     */
    UserInfoVO currentUser();

    /**
     * 当前登录用户可访问的动态路由树，供前端注册 vue-router 路由。
     *
     * <p>只返回 {@code sys_role_menu} 中授权给该用户角色的菜单，与权限点无关。
     */
    List<RouteVO> routes();
}

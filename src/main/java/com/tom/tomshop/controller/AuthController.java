package com.tom.tomshop.controller;

import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.dto.LoginDTO;
import com.tom.tomshop.dto.RefreshTokenDTO;
import com.tom.tomshop.dto.RegisterDTO;
import com.tom.tomshop.service.AuthService;
import com.tom.tomshop.vo.LoginVO;
import com.tom.tomshop.vo.RouteVO;
import com.tom.tomshop.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。
 *
 * <p>放行规则（见 {@code SaTokenConfig}）：{@code login} / {@code register} / {@code refresh}
 * 三个端点在拦截器白名单里；{@code logout} / {@code me} / {@code routes} 必须已登录。
 *
 * @author Tom
 */
@Tag(name = "00. 认证", description = "登录、注册、令牌刷新、当前用户与动态路由")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录",
            description = "返回 access token（2 小时）与 refresh token（7 天）。"
                    + "access token 放在请求头 Authorization，值为 \"Bearer <accessToken>\"")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "注册",
            description = "B2C 用户自助注册，自动绑定 USER 角色并直接返回登录令牌。")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    @Operation(summary = "刷新令牌",
            description = "用 refresh token 换一对新令牌。旧 refresh token 立即失效（轮换），"
                    + "重复使用同一个 refresh token 会被拒绝")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refresh(dto));
    }

    @Operation(summary = "登出", description = "销毁当前会话，并吊销该用户全部 refresh token")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "当前用户信息", description = "含角色编码与权限标识列表，供前端控制按钮级显隐")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.success(authService.currentUser());
    }

    @Operation(summary = "动态路由",
            description = "返回当前用户可访问的路由树，前端直接 addRoute 注册。"
                    + "字段与 vue-router 对齐：path/name/component/meta/children/redirect")
    @GetMapping("/routes")
    public Result<List<RouteVO>> routes() {
        return Result.success(authService.routes());
    }
}

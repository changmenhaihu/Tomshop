package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录/注册/刷新令牌的统一返回体。
 *
 * <p>三个场景返回同一种结构，前端只需要一套「拿到令牌后写入本地存储」的逻辑，
 * 不必区分「首次登录」还是「静默续期」。
 *
 * @author Tom
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "访问令牌，放入请求头 Authorization: Bearer <accessToken>")
    private String accessToken;

    @Schema(description = "刷新令牌，仅在 access token 过期后调用 /auth/refresh 时使用")
    private String refreshToken;

    @Schema(description = "令牌类型，固定为 Bearer", example = "Bearer")
    private String tokenType;

    @Schema(description = "access token 剩余有效期（秒）", example = "7200")
    private Long expiresIn;

    @Schema(description = "当前登录用户信息（含角色与权限）")
    private UserInfoVO user;
}

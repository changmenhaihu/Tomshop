package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 刷新令牌入参。
 *
 * <p>refresh token 放在请求体而不是 {@code Authorization} 头里：请求头那个位置留给
 * access token，由 Sa-Token 拦截器直接读取。两者混用会导致刷新接口被当成「已登录请求」，
 * 从而绕过「refresh token 只能用于刷新」的约束。
 *
 * @author Tom
 */
@Data
@Schema(description = "刷新令牌请求")
public class RefreshTokenDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "登录时下发的 refreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}

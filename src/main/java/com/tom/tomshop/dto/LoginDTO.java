package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录入参。
 *
 * <p>校验只做「格式是否合理」，不区分「账号不存在」与「密码错误」—— 两者在
 * Service 层都返回同一句提示，避免攻击者用登录接口枚举有效账号。
 *
 * @author Tom
 */
@Data
@Schema(description = "登录请求")
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入用户名")
    @Size(max = 64, message = "用户名长度不能超过 64")
    @Schema(description = "登录账号", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    @Schema(description = "登录密码", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

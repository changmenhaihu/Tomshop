package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 注册入参。
 *
 * <p>只开放 B2C 消费者注册，注册出来的账号一律绑定 {@code USER} 角色；
 * 管理端账号不经过此接口创建（否则任何人都能给自己注册一个后台账号）。
 *
 * @author Tom
 */
@Data
@Schema(description = "注册请求")
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入用户名")
    @Size(min = 4, max = 32, message = "用户名长度需在 4-32 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Schema(description = "登录账号", example = "tom2026", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    @Schema(description = "登录密码（明文传输依赖 HTTPS）", example = "tom123456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 32, message = "昵称长度不能超过 32")
    @Schema(description = "昵称，不填则默认与用户名一致", example = "Tom")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    @Schema(description = "邮箱", example = "tom@example.com")
    private String email;
}

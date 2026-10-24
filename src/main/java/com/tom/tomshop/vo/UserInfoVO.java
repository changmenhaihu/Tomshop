package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前登录用户信息。
 *
 * <p><b>刻意不包含 password</b>：接口层永不返回密码哈希，即便它是不可逆的 ——
 * 泄漏哈希等于把离线爆破的原材料送出去。同理也不返回 {@code deleted} / {@code version}
 * 这类内部列（因此本类不继承实体，而是独立 POJO）。
 *
 * <p>{@code permissions} 与 {@code roles} 直接从 Sa-Token 的权限数据源取，
 * 与后端鉴权用的是同一份数据，不会出现「前端以为有权限、后端拒绝」的错位。
 *
 * @author Tom
 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID（序列化为字符串，防止 JS 精度丢失）")
    private Long id;

    @Schema(description = "登录账号", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "超级管理员")
    private String nickname;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别：0未知 1男 2女")
    private Integer gender;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "角色编码列表，如 [\"ADMIN\"]")
    private List<String> roles;

    @Schema(description = "权限标识列表，如 [\"product:spu:save\"]")
    private List<String> permissions;
}

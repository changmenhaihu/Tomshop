package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体（表 sys_user）。
 *
 * <p>安全约定：{@code password} 只存哈希，绝不存明文。阶段 3 起由
 * {@code BCryptPasswordEncoder} 承担加密（同明文每次结果不同，自带随机盐）；
 * 本类在任何场景下都不得把 password 序列化到响应（返回给前端的是 {@code UserInfoVO}，
 * 其中不含该字段）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号 */
    private String username;

    /** 密码哈希（BCrypt） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 账号状态：1正常 0封禁 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}

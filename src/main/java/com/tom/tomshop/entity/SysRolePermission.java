package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联实体（表 sys_role_permission）。
 *
 * <p>与 {@link SysUserRole} 同理，纯关系表不继承 {@code BaseEntity}。
 *
 * <p>列名约定：被授权对象一律用「语义化列名」而非通用的 {@code xxx_id} 之外的写法 ——
 * 本表是 {@code (role_id, perm_id)}，{@link SysRoleMenu} 是 {@code (role_id, menu_id)}，
 * 两张关联表风格一致，读 SQL 时不用回头确认某个 id 到底指向权限还是菜单。
 * 注意主表 {@code sys_permission} 的主键列仍叫 {@code id}（沿用 BaseEntity 统一约定），
 * 因此 JOIN 写作 {@code ON srp.perm_id = p.id}。
 *
 * @author Tom
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID（sys_permission.id） */
    private Long permId;
}

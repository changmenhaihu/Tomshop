package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联实体（表 sys_role_menu）。
 *
 * <p>与 {@link SysRolePermission} 结构完全平行、语义互不推导：本表授权「能看见什么」，
 * 后者授权「能做什么」。纯关系表，不继承 {@code BaseEntity}，走物理删除。
 *
 * @author Tom
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 菜单 ID */
    private Long menuId;
}

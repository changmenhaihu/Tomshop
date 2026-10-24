package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限点实体（表 sys_permission）。
 *
 * <p>职责边界：本表只回答「后端允许执行什么操作」，**扁平结构、无父子层级**。
 * 前端导航由 {@link SysMenu} 独立承担，两者之间没有外键或结构性关联 ——
 * 授权通过 {@link SysRolePermission}（权限）与 {@link SysRoleMenu}（菜单）两条
 * 彼此独立的链路完成。
 *
 * <p>{@code perms} 在库中有唯一约束，是 Sa-Token 权限注解（如
 * {@code @SaCheckPermission("product:spu:save")}）的比对依据。
 * 比对时 Sa-Token 回调 {@code StpInterfaceImpl#getPermissionList}，
 * 由该方法一次 JOIN 查出当前账号的全部权限标识。
 *
 * @author Tom
 * @see SysMenu 菜单（导航结构）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 权限名称 */
    private String name;

    /** 权限标识，格式 module:resource:action，如 system:user:query（库中唯一） */
    private String perms;

    /** 类型：1按钮 2接口 */
    private Integer type;

    /** 所属业务模块，仅用于后台分组展示，与菜单无结构性关联 */
    private String module;

    /** 权限说明 */
    private String description;

    /** 状态：1启用 0禁用 */
    private Integer status;
}

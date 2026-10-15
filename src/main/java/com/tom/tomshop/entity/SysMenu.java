package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单实体（表 sys_menu），树形导航结构。
 *
 * <p>职责边界：本表只回答「前端能渲染出哪些导航入口」，**不携带任何权限标识**。
 * 一个菜单能否被某角色看到，取决于 {@link SysRoleMenu} 中是否存在对应关联，
 * 与 {@link SysPermission} 完全无关 —— 挂上了菜单不等于拥有该菜单下所有按钮权限，
 * 反之亦然。
 *
 * <p>树形通过 {@code parentId} 自关联（0 表示根），层级固定为两级：
 * {@code menuType=1} 目录（仅作分组，{@code component} 为空），
 * {@code menuType=2} 菜单（对应一个前端路由与组件）。
 *
 * @author Tom
 * @see SysPermission 权限点
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单 ID，0 表示根菜单 */
    private Long parentId;

    /** 菜单名称 */
    private String name;

    /** 类型：1目录 2菜单 */
    private Integer menuType;

    /** 前端路由路径 */
    private String path;

    /** 前端组件路径，目录类型为空 */
    private String component;

    /** 图标 */
    private String icon;

    /** 同级排序，值越小越靠前 */
    private Integer sort;

    /** 是否隐藏：0显示 1隐藏 */
    private Integer visible;

    /** 状态：1启用 0禁用 */
    private Integer status;
}

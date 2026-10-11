package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体（表 sys_permission），树形结构支撑「注解 + 动态权限」。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 父权限 ID，0 表示根节点 */
    private Long parentId;

    /** 权限名称 */
    private String name;

    /** 类型：1目录 2菜单 3按钮/接口 */
    private Integer type;

    /** 前端路由或后端接口路径 */
    private String path;

    /** 权限标识（Shiro 注解用），如 sys:user:list */
    private String perms;

    /** 图标 */
    private String icon;

    /** 同级排序，值越小越靠前 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;
}

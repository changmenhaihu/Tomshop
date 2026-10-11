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
 * @author Tom
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;
}

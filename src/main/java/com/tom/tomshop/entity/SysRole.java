package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（表 sys_role）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色名称（展示用） */
    private String roleName;

    /** 角色编码（鉴权用，如 ADMIN） */
    private String roleCode;

    /** 角色描述 */
    private String description;

    /** 状态：1启用 0禁用 */
    private Integer status;
}

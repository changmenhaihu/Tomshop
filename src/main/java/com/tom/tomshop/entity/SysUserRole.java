package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体（表 sys_user_role）。
 *
 * <p>关联表刻意不继承 {@code BaseEntity}：无逻辑删除、无乐观锁、无审计字段，
 * 走物理删除。这类「纯关系」表不承载业务状态，套用 BaseEntity 反而引入无用列。
 *
 * @author Tom
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}

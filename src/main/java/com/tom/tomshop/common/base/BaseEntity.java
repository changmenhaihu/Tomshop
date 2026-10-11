package com.tom.tomshop.common.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：所有业务表统一携带以下字段。
 *
 * <p><b>数据库建表约定</b>（阶段 1 的 schema.sql 必须为每张业务表包含）：
 * <pre>
 *   id          BIGINT       NOT NULL COMMENT '主键，雪花算法生成'
 *   create_time DATETIME     NOT NULL COMMENT '创建时间'
 *   update_time DATETIME     NOT NULL COMMENT '更新时间'
 *   deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除'
 *   version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'
 * </pre>
 *
 * <p>四个审计/控制字段由 {@code MybatisMetaObjectHandler} 自动填充，业务代码不要手工 set。
 * {@code createTime} / {@code updateTime} 上的 fill 策略与 {@code BaseEntity} 保持一致即可，
 * 无需在子类重复声明。
 *
 * @author Tom
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：雪花算法，适合后续分库分表且不暴露自增步长 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：配合 application.yml 中 logic-delete-field=deleted，SELECT 自动追加 deleted = 0 */
    @TableLogic
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    private Integer deleted;

    /** 乐观锁版本号：配合 OptimisticLockerInnerInterceptor，更新时自动 version = version + 1 */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Integer version;
}

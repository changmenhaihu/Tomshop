package com.tom.tomshop.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.tom.tomshop.common.constant.CommonConstants;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。
 *
 * <p>配合 {@code BaseEntity} 上的 {@code @TableField(fill = ...)} 生效。
 * 使用 {@code strictInsertFill} / {@code strictUpdateFill}：仅在字段存在且为 null 时填充，
 * 因此业务代码显式赋值（例如数据迁移需要保留原 create_time）不会被覆盖。
 *
 * <p>时间统一取应用服务器时间。若后续有多节点时钟漂移问题，可改为由数据库
 * {@code DEFAULT CURRENT_TIMESTAMP} 承担，但那样会失去「同一次请求内时间一致」的可控性。
 *
 * @author Tom
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_DELETED = "deleted";
    private static final String FIELD_VERSION = "version";

    /** 乐观锁初始版本号 */
    private static final int INITIAL_VERSION = 0;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, FIELD_DELETED, Integer.class, CommonConstants.NOT_DELETED);
        strictInsertFill(metaObject, FIELD_VERSION, Integer.class, INITIAL_VERSION);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}

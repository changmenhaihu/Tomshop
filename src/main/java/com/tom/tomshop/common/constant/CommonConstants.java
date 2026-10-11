package com.tom.tomshop.common.constant;

/**
 * 全局通用常量。
 *
 * <p>约定：常量类一律 {@code final} + 私有构造，禁止被继承或实例化。
 * 各业务模块的专用常量（缓存 Key、MQ Topic 等）放在同包的独立常量类中，不要堆在这里。
 *
 * @author Tom
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 业务接口统一前缀，Controller 的 {@code @RequestMapping} 必须基于它拼接 */
    public static final String API_PREFIX = "/api/v1";

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除（与 application.yml 中 mybatis-plus.global-config.db-config.logic-delete-value 保持一致） */
    public static final int DELETED = 1;

    /** 是 / 否 语义标记 */
    public static final int YES = 1;
    public static final int NO = 0;

    /** 日期时间格式（JacksonConfig 与业务格式化共用，禁止各处硬编码 pattern） */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    /** 统一时区 */
    public static final String TIME_ZONE = "GMT+8";
}

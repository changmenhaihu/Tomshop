package com.tom.tomshop.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 健康检查内部数据载体。
 *
 * <p>为什么健康检查也要有 DTO：Service 层要求「探活」并收集原始指标，
 * 而「如何呈现状态」属于展示层职责。DTO 承载事实（db 是否可用），
 * VO 承载结论（status = UP / DEGRADED），两者之间的派生逻辑由 MapStruct 转换器承担。
 * 这也是 DTO → VO 转换的标准形态，阶段 1 之后的商品/订单模块沿用同一模式。
 *
 * @author Tom
 */
@Data
public class HealthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 应用名称，取自 spring.application.name */
    private String applicationName;

    /** 应用版本号 */
    private String applicationVersion;

    /** 当前激活的环境标识 */
    private String activeProfile;

    /** MySQL 是否可用 */
    private boolean databaseAvailable;

    /** Redis 是否可用 */
    private boolean redisAvailable;

    /** JVM 版本 */
    private String javaVersion;

    /** 操作系统信息 */
    private String osName;

    /** 服务端采集时间 */
    private LocalDateTime serverTime;
}

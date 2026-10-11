package com.tom.tomshop.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 服务健康状态。
 *
 * @author Tom
 */
@Getter
@RequiredArgsConstructor
public enum HealthStatus {

    /** 全部依赖可用 */
    UP("UP", "全部依赖可用，服务正常"),

    /** 部分依赖不可用，读服务仍可提供 */
    DEGRADED("DEGRADED", "部分依赖不可用，服务降级运行"),

    /** 服务不可用 */
    DOWN("DOWN", "服务不可用");

    private final String code;

    private final String description;
}

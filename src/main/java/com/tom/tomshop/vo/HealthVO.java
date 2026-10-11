package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 健康检查对外视图。
 *
 * <p>VO 与 DTO 的差异即展示层的增值：把「db 可用 / redis 不可用」这类事实，
 * 归纳为单一的整体结论 {@code status}，让前端与运维不需要自己判断组合逻辑。
 *
 * @author Tom
 */
@Data
@Schema(description = "健康检查结果")
public class HealthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用名称", example = "tomshop")
    private String applicationName;

    @Schema(description = "应用版本号", example = "1.0.0-SNAPSHOT")
    private String applicationVersion;

    @Schema(description = "当前激活的环境", example = "dev")
    private String activeProfile;

    @Schema(description = "整体状态：UP 全部可用 / DEGRADED 部分降级", example = "UP")
    private String status;

    @Schema(description = "MySQL 是否可用", example = "true")
    private boolean databaseAvailable;

    @Schema(description = "Redis 是否可用", example = "true")
    private boolean redisAvailable;

    @Schema(description = "JVM 版本", example = "21.0.11")
    private String javaVersion;

    @Schema(description = "操作系统信息", example = "Windows 11 amd64")
    private String osName;

    @Schema(description = "服务端采集时间", example = "2026-10-08 12:35:00")
    private LocalDateTime serverTime;
}

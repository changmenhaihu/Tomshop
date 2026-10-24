package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 上下架 / 启用停用状态变更入参。
 *
 * <p>用一个最小 DTO 而不是 {@code @RequestParam Integer status}：状态是变更的主体内容，
 * 放进请求体比挂在 URL 上更符合 PUT 语义，也能留住 {@code @Min/@Max} 的校验能力
 * （查询参数上的约束校验在部分容器下会被跳过）。
 *
 * @author Tom
 */
@Data
@Schema(description = "状态变更入参")
public class StatusUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "状态：1启用/上架 0停用/下架", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}

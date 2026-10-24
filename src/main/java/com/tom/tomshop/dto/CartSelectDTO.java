package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 购物车勾选/取消勾选入参，支持批量与全选。
 *
 * @author Tom
 */
@Data
@Schema(description = "购物车勾选入参")
public class CartSelectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "条目 ID 列表。为 null 或空数组表示【全选/全不选】当前用户购物车中的全部条目",
            example = "[1, 2, 3]")
    private List<Long> itemIds;

    @Schema(description = "目标勾选状态：true 选中 false 取消", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "勾选状态不能为空")
    private Boolean selected;
}

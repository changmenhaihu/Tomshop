package com.tom.tomshop.dto;

import com.tom.tomshop.common.constant.CartConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 加入购物车入参。
 *
 * @author Tom
 */
@Data
@Schema(description = "加入购物车入参")
public class CartItemAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "SKU ID", example = "10001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    @Schema(description = "购买数量", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = CartConstants.MAX_QUANTITY_PER_SKU, message = "单个规格最多购买 " + CartConstants.MAX_QUANTITY_PER_SKU + " 件")
    private Integer quantity;
}

package com.tom.tomshop.dto;

import com.tom.tomshop.common.constant.CartConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改购物车条目数量入参。
 *
 * <p>语义是<b>设值</b>而非累加：用户在数量选择器上把 3 改成 5，期望的就是 5，
 * 而不是 3+5=8。累加语义只存在于「加入购物车」这个动作里。
 *
 * @author Tom
 */
@Data
@Schema(description = "修改购物车数量入参")
public class CartItemQuantityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "修改后的数量（绝对值，非增量）", example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = CartConstants.MAX_QUANTITY_PER_SKU, message = "单个规格最多购买 " + CartConstants.MAX_QUANTITY_PER_SKU + " 件")
    private Integer quantity;
}

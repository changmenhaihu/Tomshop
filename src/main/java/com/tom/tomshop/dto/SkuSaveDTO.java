package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品 SKU 新增/更新入参（作为 {@link SpuSaveDTO#getSkuList()} 的元素使用）。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品规格（SKU）入参")
public class SkuSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "SKU ID。新增时不传，更新时传 —— 带上表示更新该 SKU，不带表示新增",
            example = "10001")
    private Long id;

    @Schema(description = "规格名称", example = "曜石黑 / 128G", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规格名称不能为空")
    @Size(max = 128, message = "规格名称长度不能超过 128")
    private String skuName;

    @Schema(description = "规格明细 JSON 字符串", example = "{\"颜色\":\"曜石黑\",\"容量\":\"128G\"}")
    @Size(max = 512, message = "规格明细长度不能超过 512")
    private String spec;

    @Schema(description = "销售单价", example = "4999.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "销售单价不能为空")
    @DecimalMin(value = "0.00", message = "销售单价不能为负数")
    // 与库中 DECIMAL(10,2) 对齐：整数最多 8 位、小数最多 2 位。
    // 不校验的话，超出精度时 MySQL 严格模式会直接报错，错误信息对前端不友好。
    @Digits(integer = 8, fraction = 2, message = "销售单价最多 8 位整数、2 位小数")
    private BigDecimal price;

    @Schema(description = "库存数量", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负数")
    private Integer stock;

    @Schema(description = "SKU 图片 URL")
    @Size(max = 512, message = "图片 URL 长度不能超过 512")
    private String image;

    @Schema(description = "状态：1上架 0下架", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "SKU 状态不能为空")
    @Min(value = 0, message = "SKU 状态只能是 0 或 1")
    @Max(value = 1, message = "SKU 状态只能是 0 或 1")
    private Integer status;
}

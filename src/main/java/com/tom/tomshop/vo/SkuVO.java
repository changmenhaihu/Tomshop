package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品 SKU 展示对象。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品规格（SKU）")
public class SkuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "SKU ID", example = "10001")
    private Long id;

    @Schema(description = "所属 SPU ID", example = "1001")
    private Long spuId;

    @Schema(description = "规格名称", example = "曜石黑 / 128G")
    private String skuName;

    @Schema(description = "规格明细 JSON 字符串", example = "{\"颜色\":\"曜石黑\",\"容量\":\"128G\"}")
    private String spec;

    @Schema(description = "销售单价", example = "4999.00")
    private BigDecimal price;

    @Schema(description = "剩余库存", example = "100")
    private Integer stock;

    @Schema(description = "累计销量", example = "800")
    private Integer sales;

    @Schema(description = "SKU 图片 URL")
    private String image;

    @Schema(description = "状态：1上架 0下架", example = "1")
    private Integer status;
}

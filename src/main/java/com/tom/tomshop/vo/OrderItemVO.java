package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细展示对象。
 *
 * <p>独立于 {@code OrderItem} 实体而非继承它：实体带 {@code deleted} / {@code version}
 * 这类审计字段，直接暴露出去会污染接口契约（见 SkuVO 的同类说明）。
 * 明细里的商品信息全部是下单时的<b>快照</b>，商品后续改名调价都不会影响这里。
 *
 * @author Tom
 */
@Data
@Schema(description = "订单明细")
public class OrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "明细 ID", example = "1900000000000000002")
    private Long id;

    @Schema(description = "订单 ID", example = "1900000000000000001")
    private Long orderId;

    @Schema(description = "订单号", example = "202610161234560001123456")
    private String orderNo;

    @Schema(description = "SPU ID", example = "1001")
    private Long spuId;

    @Schema(description = "SKU ID", example = "10001")
    private Long skuId;

    @Schema(description = "商品名称（下单时快照）", example = "X 旗舰智能手机")
    private String spuName;

    @Schema(description = "规格名称（下单时快照）", example = "曜石黑 / 128G")
    private String skuName;

    @Schema(description = "规格明细 JSON（下单时快照）")
    private String skuSpec;

    @Schema(description = "商品图（下单时快照）")
    private String productImage;

    @Schema(description = "成交单价（下单时快照）", example = "4999.00")
    private BigDecimal price;

    @Schema(description = "购买数量", example = "2")
    private Integer quantity;

    @Schema(description = "小计 = 单价 × 数量", example = "9998.00")
    private BigDecimal totalAmount;
}

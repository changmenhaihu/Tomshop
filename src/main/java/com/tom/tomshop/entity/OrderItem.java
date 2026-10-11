package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体（表 order_item）。
 *
 * <p>商品信息（名称/规格/单价/图片）均为下单时的「快照」，即使商品后续下架、
 * 改名、调价，历史订单明细也保持不变。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    /** 所属订单 ID */
    private Long orderId;

    /** 订单号（冗余，便于明细独立查询） */
    private String orderNo;

    /** SPU ID（快照来源） */
    private Long spuId;

    /** SKU ID（快照来源） */
    private Long skuId;

    /** 商品名称（快照） */
    private String spuName;

    /** 规格名称（快照） */
    private String skuName;

    /** 规格明细 JSON（快照） */
    private String skuSpec;

    /** 商品图片（快照） */
    private String productImage;

    /** 成交单价（快照） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 小计 = 单价 * 数量 */
    private BigDecimal totalAmount;
}

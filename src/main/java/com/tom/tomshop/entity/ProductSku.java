package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 SKU 实体（表 product_sku）。
 *
 * <p>SKU（库存计价单元）回答「卖的是哪一款」，库存与真实售价在此维护。
 * {@code version} 乐观锁字段是并发扣减库存的根基（阶段 2 下单、阶段 4 一致性都依赖它）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {

    /** 所属 SPU ID */
    private Long spuId;

    /** 规格名称，如「红色/XL」 */
    private String skuName;

    /** 规格明细，JSON 对象字符串，如 {"颜色":"红","尺码":"XL"} */
    private String spec;

    /** 销售单价 */
    private BigDecimal price;

    /** 库存数量（并发扣减靠 version 乐观锁） */
    private Integer stock;

    /** 该 SKU 累计销量 */
    private Integer sales;

    /** SKU 图片 URL */
    private String image;

    /** 状态：1上架 0下架 */
    private Integer status;
}

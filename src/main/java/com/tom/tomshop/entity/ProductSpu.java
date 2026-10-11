package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 SPU 实体（表 product_spu）。
 *
 * <p>SPU（标准化产品单元）回答「这是什么商品」，不含规格差异；规格差异由
 * {@link ProductSku} 承载。{@code price} 是展示价（取该 SPU 下 SKU 的最低价），
 * 库存与真实售价一律看 SKU。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_spu")
public class ProductSpu extends BaseEntity {

    /** 所属分类 ID */
    private Long categoryId;

    /** 商品名称 */
    private String name;

    /** 副标题/卖点 */
    private String subtitle;

    /** 主图 URL */
    private String mainImage;

    /** 轮播图，JSON 数组字符串 */
    private String images;

    /** 富文本详情（HTML） */
    private String detail;

    /** 展示价（SKU 最低价） */
    private BigDecimal price;

    /** 累计销量 */
    private Integer sales;

    /** 状态：1上架 0下架 */
    private Integer status;
}

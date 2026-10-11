package com.tom.tomshop.vo;

import com.tom.tomshop.entity.ProductSpu;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品 SPU 详情视图（阶段 1 用于演示自定义 ResultMap 的关联查询）。
 *
 * <p>继承 {@link ProductSpu} 以便复用 {@code autoMapping} 自动映射同名列，
 * 额外平铺一个 {@code categoryName} 承载「商品 → 分类」的多对一关联结果。
 *
 * <p><b>注意</b>：这是「查询结果视图」，会带出 {@code deleted} / {@code version} 等内部字段，
 * 不直接对外暴露。阶段 2 会定义不含内部字段的对外 VO 并经 MapStruct 转换。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpuDetailVO extends ProductSpu {

    /** 所属分类名称（来自 product_category.name，多对一关联） */
    private String categoryName;
}

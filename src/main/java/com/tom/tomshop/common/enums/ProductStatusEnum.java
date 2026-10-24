package com.tom.tomshop.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品上下架状态，与 {@code product_category.status} / {@code product_spu.status} /
 * {@code product_sku.status} 三张表的取值语义保持一致。
 *
 * @author Tom
 */
@Getter
@RequiredArgsConstructor
public enum ProductStatusEnum {

    /** 下架：前端不可见、不可加购、不可下单 */
    OFF_SHELF(0, "下架"),
    /** 上架：可正常浏览与购买 */
    ON_SHELF(1, "上架");

    private final Integer code;

    private final String desc;

    public static ProductStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 是否处于可售状态。商品能否下单要同时满足「自身上架」与「所属分类上架」，
     * 调用方需对两层分别判断。
     */
    public boolean onSale() {
        return this == ON_SHELF;
    }
}

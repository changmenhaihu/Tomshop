package com.tom.tomshop.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 下单来源，决定「要买什么」是从哪里来的。
 *
 * <p>两种来源在结算逻辑上完全一致（都要校验商品、扣库存、建订单与明细），
 * 差别只在于**商品清单的获取方式**与**下单后是否清理购物车**：
 * <pre>
 *   CART   从购物车已选中的条目下单 -> 下单成功后删除这些条目
 *   DIRECT 立即购买，明细由入参直接给定 -> 不触碰购物车
 * </pre>
 *
 * @author Tom
 */
@Getter
@RequiredArgsConstructor
public enum OrderSourceEnum {

    /** 购物车结算 */
    CART(1, "购物车"),
    /** 立即购买 */
    DIRECT(2, "立即购买");

    private final Integer code;

    private final String desc;

    public static OrderSourceEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderSourceEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}

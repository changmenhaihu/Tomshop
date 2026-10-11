package com.tom.tomshop.vo;

import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.entity.OrderItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 订单详情视图（阶段 1 用于演示自定义 ResultMap 的一对多关联）。
 *
 * <p>继承 {@link OrderInfo} 复用 autoMapping，并通过 {@code items} 承载
 * 「订单 → 明细」的一对多关联（collection 映射）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailVO extends OrderInfo {

    /** 订单明细列表（一对多关联） */
    private List<OrderItem> items;
}

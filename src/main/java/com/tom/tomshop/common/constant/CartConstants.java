package com.tom.tomshop.common.constant;

/**
 * 购物车模块常量。
 *
 * <p>按项目约定，全局通用常量放 {@link CommonConstants}，模块内常量放各自的常量类，
 * 避免把所有东西都堆进一个类里。
 *
 * @author Tom
 */
public final class CartConstants {

    /**
     * 单个 SKU 在购物车中的最大数量。
     *
     * <p>设上限的目的不是业务正确性，而是防止异常客户端把 quantity 刷成 21 亿
     * —— 那会让后续「quantity × 单价」的金额计算出 BigDecimal 溢出或生成天文数字订单。
     * 下单时会用库存再挡一次，但购物车阶段就该拦住明显不合理的输入。
     */
    public static final int MAX_QUANTITY_PER_SKU = 999;

    /**
     * 勾选状态：已选中。
     *
     * <p>用 {@code Integer} 而非 {@code int} 声明，是为了能直接塞进 {@code LambdaUpdateWrapper#set}
     * 与实体字段比较，省掉调用处反复装箱。
     */
    public static final Integer SELECTED_YES = 1;

    /** 勾选状态：未选中。 */
    public static final Integer SELECTED_NO = 0;

    private CartConstants() {
    }
}

package com.tom.tomshop.common.constant;

import java.math.BigDecimal;

/**
 * 订单模块常量。
 *
 * @author Tom
 */
public final class OrderConstants {

    /**
     * 包邮门槛：商品总额达到该金额即免运费。
     *
     * <p>用 {@code BigDecimal} 而非 double 参与金额比较 —— 浮点数在
     * {@code 98.99999999} 这类边界上会给出与用户直觉相反的判断。
     */
    public static final BigDecimal FREE_FREIGHT_THRESHOLD = new BigDecimal("99.00");

    /** 未达包邮门槛时收取的固定运费 */
    public static final BigDecimal FLAT_FREIGHT = new BigDecimal("12.00");

    /**
     * 订单号生成的最大重试次数。
     *
     * <p>订单号是「时间戳 + 用户尾号 + 6 位随机数」，天然存在极小概率碰撞。
     * 唯一索引 {@code uk_order_no} 是最终兜底，但直接让它报错会以
     * {@code DuplicateKeyException} 的形式变成 5000 系统异常 —— 用户重试一次就好，
     * 不该是系统错误。因此在应用层先查后插并重试若干次。
     */
    public static final int ORDER_NO_MAX_RETRY = 5;

    private OrderConstants() {
    }
}

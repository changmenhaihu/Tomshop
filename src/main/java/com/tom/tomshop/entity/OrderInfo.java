package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体（表 order_info）。
 *
 * <p><b>快照设计</b>：收货信息（receiver_*）与金额在下单时固化，
 * 不随地址表/商品价格后续变更而改变，保证历史订单可追溯、可对账。
 * 订单明细的商品快照见 {@link OrderItem}。
 *
 * <p>状态流转：10待支付 → 20已支付 → 30已发货 → 40已完成；异常分支 50已取消 / 60已关闭。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_info")
public class OrderInfo extends BaseEntity {

    /** 订单号（业务号，唯一，对客展示） */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 商品总额 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 实付金额 = 总额 - 优惠 + 运费 */
    private BigDecimal payAmount;

    /** 支付方式：1支付宝 2微信 3银行卡 */
    private Integer payType;

    /** 订单状态：10待支付 20已支付 30已发货 40已完成 50已取消 60已关闭 */
    private Integer status;

    /** 收货人姓名（快照） */
    private String receiverName;

    /** 收货人手机号（快照） */
    private String receiverPhone;

    /** 收货地址（快照） */
    private String receiverAddress;

    /** 买家备注 */
    private String remark;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;
}

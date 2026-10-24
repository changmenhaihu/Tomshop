package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 下单前预览（结算页）。
 *
 * <p><b>为什么需要这个接口</b>：运费规则（满额包邮、否则固定运费）是服务端逻辑，
 * 前端算不出来。若不提供预览，结算页要么不显示运费、要么前端把规则抄一份 ——
 * 后者在规则调整时必然与后端不一致，用户看到的金额和实际扣款不符。
 * 因此把「金额怎么算」的唯一事实来源留在服务端，前端只负责展示。
 *
 * <p>本接口<b>只读</b>：不锁定库存、不生成订单。用户可以反复刷新结算页。
 *
 * @author Tom
 */
@Data
@Schema(description = "下单前预览")
public class OrderPreviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待结算的商品明细（购物车来源时即已勾选且可结算的条目）")
    private List<OrderItemVO> items = new ArrayList<>();

    @Schema(description = "收货人姓名", example = "张三")
    private String receiverName;

    @Schema(description = "收货人手机号", example = "13800138000")
    private String receiverPhone;

    @Schema(description = "收货地址（省市区 + 详细地址拼接）",
            example = "广东省 深圳市 南山区 科技园路 1 号")
    private String receiverAddress;

    @Schema(description = "商品总额", example = "9998.00")
    private BigDecimal totalAmount;

    @Schema(description = "运费；达到包邮门槛时为 0", example = "0.00")
    private BigDecimal freightAmount;

    @Schema(description = "优惠金额（阶段 5 引入优惠券后启用，当前恒为 0）", example = "0.00")
    private BigDecimal discountAmount;

    @Schema(description = "应付金额 = 商品总额 - 优惠 + 运费", example = "9998.00")
    private BigDecimal payAmount;

    @Schema(description = "商品总件数", example = "2")
    private Integer totalQuantity;
}

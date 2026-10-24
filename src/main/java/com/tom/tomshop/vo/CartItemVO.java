package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车条目展示对象。
 *
 * <p><b>为什么带上商品快照信息（spuName / skuName / price / image）</b>：
 * 购物车列表要把商品名、图片、价格一并渲染出来。只返回 {@code skuId} 的话，
 * 前端得为每个条目再调一次商品详情接口 —— 10 件商品就是 11 次请求。
 * 这里由服务端一次性组装好（虽然有 N+1 查询，但购物车条目数天然很少，通常个位数）。
 *
 * <p><b>失效条目不隐藏、而是标记</b>：商品可能在使用者加购后被下架或删除。
 * 直接从列表里过滤掉会让用户困惑（「我明明加了，怎么没了」），
 * 因此保留条目并通过 {@link #available} 与 {@link #unavailableReason} 说明原因，
 * 由前端置灰展示并禁用结算勾选。
 *
 * @author Tom
 */
@Data
@Schema(description = "购物车条目")
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "购物车条目 ID", example = "2110760184231698434")
    private Long id;

    @Schema(description = "SKU ID", example = "10001")
    private Long skuId;

    @Schema(description = "SPU ID", example = "1001")
    private Long spuId;

    @Schema(description = "商品名称", example = "X 旗舰智能手机")
    private String spuName;

    @Schema(description = "规格名称", example = "曜石黑 / 128G")
    private String skuName;

    @Schema(description = "规格明细 JSON 字符串")
    private String spec;

    @Schema(description = "商品图 URL")
    private String image;

    @Schema(description = "当前单价（实时取 SKU 价格，非加购时快照）", example = "4999.00")
    private BigDecimal price;

    @Schema(description = "数量", example = "2")
    private Integer quantity;

    @Schema(description = "是否勾选：1选中 0未选中", example = "1")
    private Integer selected;

    @Schema(description = "当前可售库存", example = "100")
    private Integer stock;

    @Schema(description = "小计 = 单价 × 数量", example = "9998.00")
    private BigDecimal subtotal;

    @Schema(description = "是否可结算。商品/规格/分类任一被下架或删除、库存不足时为 false",
            example = "true")
    private Boolean available;

    @Schema(description = "不可结算的原因；available=true 时为 null", example = "商品已下架")
    private String unavailableReason;
}

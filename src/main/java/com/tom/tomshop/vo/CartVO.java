package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车整体视图：条目列表 + 汇总信息。
 *
 * <p><b>为什么汇总金额由服务端算</b>：选中商品合计金额是结算的依据。
 * 若交给前端遍历求和，金额计算就散落在客户端，一旦后续加入优惠券、
 * 运费规则，前后端各算一遍迟早对不上。金额只认服务端一个来源。
 *
 * <p>注意 {@link #selectedAmount} 统计的是<b>可结算且已勾选</b>的条目 ——
 * 失效条目即便处于勾选状态也不计入，否则用户看到的合计与最终下单金额会不一致。
 *
 * @author Tom
 */
@Data
@Schema(description = "购物车视图")
public class CartVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "购物车条目列表")
    private List<CartItemVO> items = new ArrayList<>();

    @Schema(description = "条目种类数", example = "3")
    private Integer itemCount;

    @Schema(description = "商品总件数（所有条目数量之和，含未勾选）", example = "7")
    private Integer totalQuantity;

    @Schema(description = "已勾选且可结算的件数", example = "4")
    private Integer selectedQuantity;

    @Schema(description = "已勾选且可结算的合计金额", example = "15996.00")
    private BigDecimal selectedAmount;

    @Schema(description = "是否存在失效条目（前端据此提示「部分商品已失效」）", example = "false")
    private Boolean hasUnavailable;
}

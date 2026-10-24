package com.tom.tomshop.dto;

import com.tom.tomshop.common.base.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单分页查询入参。
 *
 * <p>可排序字段白名单由 Service 限定为 {@code createTime} / {@code payAmount}，
 * 不支持按 {@code status} 排序 —— 状态是离散值，按它排序得到的分组顺序没有业务含义。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单分页查询入参")
public class OrderQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "订单状态：10待支付 20已支付 30已发货 40已完成 50已取消 60已关闭。不传查全部",
            example = "10")
    private Integer status;

    @Schema(description = "订单号模糊匹配", example = "20261016")
    private String orderNo;
}

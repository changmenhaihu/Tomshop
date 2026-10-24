package com.tom.tomshop.dto;

import com.tom.tomshop.common.enums.OrderSourceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交订单入参。
 *
 * <p><b>两种来源共用同一个接口</b>：购物车结算与「立即购买」在服务端要做的事
 * （校验商品、扣减库存、落订单与明细）完全一致，差别只是商品清单从哪来。
 * 拆成两个接口会让这套核心逻辑复制两份，是最容易随时间跑偏的写法。
 * 因此用 {@link #source} 区分，服务端按来源决定「清单从购物车取」还是「用本入参的 skuId」。
 *
 * <p>{@code skuId} / {@code quantity} 仅在 {@code source=DIRECT} 时必填，
 * 这类「按另一个字段决定是否必填」的约束用 Bean Validation 的注解很难表达
 * （需要自定义跨字段校验器），放在 Service 里显式判断并给出明确错误信息更直接。
 *
 * @author Tom
 */
@Data
@Schema(description = "提交订单入参")
public class OrderSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "下单来源：1购物车结算 2立即购买", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "下单来源不能为空")
    private Integer source;

    @Schema(description = "收货地址 ID", example = "1900000000000000001",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    @Schema(description = "SKU ID。source=2 时必填；source=1 时忽略", example = "10001")
    private Long skuId;

    @Schema(description = "购买数量。source=2 时必填", example = "1")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = Integer.MAX_VALUE, message = "购买数量超出范围")
    private Integer quantity;

    @Schema(description = "支付方式：1支付宝 2微信 3银行卡。下单时不支付，仅记录用户意向",
            example = "1")
    private Integer payType;

    @Schema(description = "买家备注", example = "工作日送达")
    @Size(max = 256, message = "备注长度不能超过 256")
    private String remark;

    /**
     * 解析下单来源枚举。
     *
     * @return 匹配的枚举；{@code null} 表示入参非法，由调用方报错
     */
    @Schema(hidden = true)
    public OrderSourceEnum resolveSource() {
        return OrderSourceEnum.of(source);
    }
}

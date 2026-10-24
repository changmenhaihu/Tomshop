package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单视图（含明细行），列表与详情共用。
 *
 * <p><b>为什么列表也带 items</b>：订单列表页要给每笔订单展示商品缩略信息
 * （图 + 名称 + 件数）。若列表不带明细，前端得为每行再调一次详情接口 —— 10 笔订单
 * 就是 11 次请求。服务端用一次 {@code IN} 查询把这一页所有订单的明细全取回来分组，
 * 总共 2 次 SQL，比 N+1 划算得多。
 *
 * <p><b>为什么不继承 {@code OrderInfo} 实体</b>：实体上的 {@code deleted} / {@code version}
 * 是持久层概念，暴露到接口里既污染契约，也可能让调用方误以为能通过接口修改它们。
 * 这里逐字段声明，宁可多写几行也换一个干净的契约。
 *
 * @author Tom
 */
@Data
@Schema(description = "订单视图（含明细）")
public class OrderDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "订单 ID", example = "1900000000000000001")
    private Long id;

    @Schema(description = "订单号（对客展示的业务号）", example = "202610161234560001123456")
    private String orderNo;

    @Schema(description = "下单用户 ID", example = "1")
    private Long userId;

    @Schema(description = "商品总额", example = "9998.00")
    private BigDecimal totalAmount;

    @Schema(description = "优惠金额", example = "0.00")
    private BigDecimal discountAmount;

    @Schema(description = "运费", example = "0.00")
    private BigDecimal freightAmount;

    @Schema(description = "实付金额 = 总额 - 优惠 + 运费", example = "9998.00")
    private BigDecimal payAmount;

    @Schema(description = "支付方式：1支付宝 2微信 3银行卡", example = "1")
    private Integer payType;

    @Schema(description = "订单状态码：10待支付 20已支付 30已发货 40已完成 50已取消 60已关闭",
            example = "10")
    private Integer status;

    @Schema(description = "状态文案，由服务端翻译，避免前端维护一份状态字典", example = "待支付")
    private String statusDesc;

    @Schema(description = "收货人姓名（下单时快照）", example = "张三")
    private String receiverName;

    @Schema(description = "收货人手机号（下单时快照）", example = "13800138000")
    private String receiverPhone;

    @Schema(description = "收货地址（下单时快照，省市区 + 详细地址拼接）",
            example = "广东省 深圳市 南山区 科技园路 1 号")
    private String receiverAddress;

    @Schema(description = "买家备注", example = "工作日送达")
    private String remark;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "发货时间")
    private LocalDateTime deliveryTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "商品总件数（各明细数量之和）", example = "2")
    private Integer totalQuantity;

    @Schema(description = "订单明细列表")
    private List<OrderItemVO> items = new ArrayList<>();

    // ==================== 状态机衍生的操作标识 ====================
    // 由服务端按 OrderStatusEnum 计算后下发。
    // 若交给前端自行判断（status == 10 才显示取消按钮），一旦状态机调整，
    // 就必须同步修改所有客户端，漏改一处就会出现「点了没反应」的按钮。

    @Schema(description = "当前是否可取消", example = "true")
    private Boolean canCancel;

    @Schema(description = "当前是否可发货（管理端使用）", example = "false")
    private Boolean canDeliver;

    @Schema(description = "当前是否可确认收货", example = "false")
    private Boolean canFinish;
}

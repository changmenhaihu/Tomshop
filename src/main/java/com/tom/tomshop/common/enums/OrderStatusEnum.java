package com.tom.tomshop.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单状态枚举，取值与 {@code order_info.status} 严格对齐。
 *
 * <p>状态机（合法流转只有以下四条路径，其余一律拒绝）：
 * <pre>
 *   10 待支付 ──支付──> 20 已支付 ──发货──> 30 已发货 ──确认收货──> 40 已完成
 *        │                   │
 *        └────取消───────────┴──> 50 已取消
 *
 *   60 已关闭：超时未支付由定时任务关闭（阶段 5 引入），人工不可直接置为该状态
 * </pre>
 *
 * <p>「能否流转」的判断集中在枚举里，而不是散落在 Service 的 if 中 —— 订单状态是最容易
 * 随着业务迭代而漏改的地方，集中一处才能保证新增状态时不遗漏分支。
 *
 * @author Tom
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatusEnum {

    /** 下单成功但未支付 */
    WAIT_PAY(10, "待支付"),
    /** 已支付，等待发货 */
    PAID(20, "已支付"),
    /** 已发货，等待买家确认收货 */
    DELIVERED(30, "已发货"),
    /** 交易完成 */
    FINISHED(40, "已完成"),
    /** 用户或系统取消 */
    CANCELLED(50, "已取消"),
    /** 超时未支付自动关闭 */
    CLOSED(60, "已关闭");

    private final Integer code;

    private final String desc;

    /** code -> 枚举，避免每次 {@link #of} 都遍历 values() */
    private static final Map<Integer, OrderStatusEnum> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(OrderStatusEnum::getCode, Function.identity()));

    /**
     * 按状态码查找枚举。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举，未匹配（含 null）返回 {@code null}，由调用方决定如何兜底
     */
    public static OrderStatusEnum of(Integer code) {
        return code == null ? null : BY_CODE.get(code);
    }

    /**
     * 是否可取消：待支付（未付款）与已支付（需退款）两种状态允许取消，
     * 一旦发货就只能走售后流程，不能再走取消。
     */
    public boolean canCancel() {
        return this == WAIT_PAY || this == PAID;
    }

    /**
     * 是否可发货：只有已支付的订单能发货。
     */
    public boolean canDeliver() {
        return this == PAID;
    }

    /**
     * 是否可确认收货。
     */
    public boolean canFinish() {
        return this == DELIVERED;
    }

    /**
     * 是否终态（终态订单不再允许任何状态变更）。
     */
    public boolean isFinal() {
        return this == FINISHED || this == CANCELLED || this == CLOSED;
    }
}

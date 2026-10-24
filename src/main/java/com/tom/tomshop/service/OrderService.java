package com.tom.tomshop.service;

import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.dto.OrderQueryDTO;
import com.tom.tomshop.dto.OrderSubmitDTO;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.OrderPreviewVO;

/**
 * 订单服务。
 *
 * <p>状态机（与 {@code OrderStatusEnum} 保持一致）：
 * <pre>
 *   10 待支付 ──支付──> 20 已支付 ──发货──> 30 已发货 ──确认收货──> 40 已完成
 *        └────取消────> 50 已取消        └────取消────> 50 已取消（回滚库存）
 * </pre>
 *
 * <p>所有状态变更都通过 {@code where id=? and user_id=? and status=?} 的条件更新实现，
 * 而不是「先查后改」：后者在高并发下两个请求可能都读到同一状态、都判定可流转，
 * 于是同一笔订单被取消两次，库存被回滚两次 —— 凭空多出来的库存最终会变成超卖。
 *
 * @author Tom
 */
public interface OrderService {

    /**
     * 提交订单。整个流程在一个事务内完成：
     * 校验商品与地址 → 逐条原子扣减库存 → 落订单主表 → 落明细 → 清理购物车（购物车来源时）。
     *
     * @param dto    下单入参
     * @param userId 下单用户
     * @return 订单视图（含明细与应付金额），前端据此跳转支付页
     * @throws com.tom.tomshop.exception.BusinessException 商品失效、库存不足、地址不存在、订单号生成失败
     */
    OrderDetailVO submit(OrderSubmitDTO dto, Long userId);

    /**
     * 下单前预览：算出商品总额、运费与应付金额，不产生任何写操作。
     *
     * @param addressId 收货地址 ID（用于回显收货信息）
     * @param userId    当前用户
     * @return 结算页所需的全部信息
     */
    OrderPreviewVO preview(Long addressId, Long userId);

    /**
     * 我的订单分页。
     *
     * <p>强制按 {@code user_id} 过滤：订单列表是最典型的越权入口，
     * 少一个 user_id 条件就意味着任何登录用户都能翻出全站订单。
     */
    PageResult<OrderDetailVO> page(OrderQueryDTO query, Long userId);

    /**
     * 订单详情（含明细）。
     *
     * @throws com.tom.tomshop.exception.BusinessException 订单不存在或不属于当前用户时抛 4001
     */
    OrderDetailVO detail(Long orderId, Long userId);

    /**
     * 取消订单。仅「待支付」与「已支付」可取消，取消时<b>回滚库存</b>。
     *
     * <p>回滚库存用「条件更新 + 只在状态确实由允许值变为已取消时才执行」的顺序保证幂等：
     * 重复调用第二次会因状态条件不匹配而影响 0 行，库存不会被二次加回。
     */
    void cancel(Long orderId, Long userId);

    /**
     * 发货（管理端操作）。仅「已支付」可发货。
     */
    void deliver(Long orderId);

    /**
     * 确认收货。仅「已发货」可确认，确认后订单进入终态。
     */
    void finish(Long orderId, Long userId);
}

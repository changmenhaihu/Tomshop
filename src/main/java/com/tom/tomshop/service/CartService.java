package com.tom.tomshop.service;

import com.tom.tomshop.dto.CartItemAddDTO;
import com.tom.tomshop.dto.CartSelectDTO;
import com.tom.tomshop.entity.CartItem;
import com.tom.tomshop.vo.CartVO;

import java.util.List;

/**
 * 购物车服务。
 *
 * <p><b>为什么每个方法都显式传 {@code userId}</b>：购物车是强用户私有数据，
 * 越权查询（A 看到 B 的购物车）是最常见的低级漏洞。把 userId 作为方法入参、
 * 并在 SQL 的 where 条件里强制带上，比在 Service 内部从 ThreadLocal 隐式取更安全 ——
 * 隐式取值在异步线程、定时任务里会拿到 null，而在方法签名上就必须提供 userId，
 * 调用方（Controller）无法「忘记」它，实现层也无从绕过。
 *
 * @author Tom
 */
public interface CartService {

    /**
     * 加入购物车。同一 SKU 重复加购走数量累加，并受单 SKU 上限约束。
     *
     * <p>加购前会校验规格可购买性（规格/商品/分类三层上架），
     * 但<b>不校验库存</b> —— 购物车只是「意向清单」，此时锁定库存会让
     * 大量只逛不买的用户把库存占死。库存在下单时才做原子扣减。
     *
     * @return 当前该 SKU 在购物车中的数量
     */
    Integer add(CartItemAddDTO dto, Long userId);

    /**
     * 查询购物车（含实时价格、库存与失效标记）。
     */
    CartVO detail(Long userId);

    /**
     * 修改条目数量（绝对值设值）。
     */
    void updateQuantity(Long itemId, Integer quantity, Long userId);

    /**
     * 删除条目（支持批量）。
     *
     * @param itemIds 条目 ID 列表；不能为空
     */
    void remove(List<Long> itemIds, Long userId);

    /**
     * 清空购物车中<b>已勾选</b>的条目 —— 下单成功后调用。
     *
     * <p>只清已勾选的：未勾选的商品用户并没有买，替他清掉等于擅自丢弃他的意向。
     * 订单模块下单成功后调用此方法，故清空范围与下单范围天然一致。
     */
    void clearSelected(Long userId);

    /**
     * 勾选 / 取消勾选。itemIds 为 null 或空表示全选或全不选。
     */
    void updateSelected(CartSelectDTO dto, Long userId);

    /**
     * 取出当前用户<b>已勾选且可结算</b>的条目，供订单模块结算。
     *
     * <p>放在购物车域内而不是让订单模块自己查 {@code cart_item}：
     * 「哪些条目算有效」这条规则（勾选 + 三层上架 + 库存充足）属于购物车的语义，
     * 复制到订单模块会随时间漂移出不一致。
     *
     * @param userId 用户 ID
     * @return 已勾选且有效的条目
     * @throws com.tom.tomshop.exception.BusinessException 购物车为空或没有可结算条目时抛 3002
     */
    List<CartItem> listSelectedForCheckout(Long userId);
}

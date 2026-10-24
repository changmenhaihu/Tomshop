package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.CartItem;
import org.apache.ibatis.annotations.Param;

/**
 * 购物车条目 Mapper。
 *
 * @author Tom
 */
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加购（存在则累加，不存在则插入），一条 SQL 完成。
     *
     * <p><b>为什么必须用 INSERT ... ON DUPLICATE KEY UPDATE，而不是「先查再插/改」</b>：
     * {@code cart_item} 上有唯一键 {@code uk_user_sku(user_id, sku_id)}，
     * 而唯一键<b>不包含 {@code deleted} 列</b> —— 也就是说，被逻辑删除的条目<b>仍然占着这个唯一键</b>。
     * 若用 MyBatis-Plus 的常规写法「selectOne 查有没有（@TableLogic 会自动过滤掉已删除行）
     * → 没有则 insert」，用户「加入购物车 → 删除 → 再加购」时就会撞上唯一键约束，
     * 抛出 {@code DuplicateKeyException} 变成 5000 系统异常。
     * 这类 bug 只在「删过再加」的路径上出现，开发自测极易漏掉。
     *
     * <p>本语句顺带处理了「复活」语义：命中重复键时把 {@code deleted} 置回 0，
     * 并把数量重置为新值（而不是在已删除条目的旧数量上累加 —— 用户眼里那条已经删了，
     * 累加会得到一个他没预期的数量）。
     *
     * <p>数量用 {@code LEAST(..., maxQuantity)} 封顶：并发加购时「累加 + 上限校验」
     * 若分两步做，两个请求可能各自校验通过后再累加，突破上限。放进 SQL 里才能原子封顶。
     *
     * @param id          MyBatis-Plus 生成的雪花 ID（表无 AUTO_INCREMENT，插入时必须显式提供）
     * @param userId      用户 ID
     * @param skuId       SKU ID
     * @param quantity    本次加购数量
     * @param maxQuantity 单 SKU 数量上限
     * @return 受影响行数。MySQL 语义下：插入返回 1，更新返回 2，值未变化返回 0 ——
     *         因此调用方不应依赖具体数值判断成败，只用它确认语句已执行
     */
    int upsertItem(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("skuId") Long skuId,
                   @Param("quantity") int quantity,
                   @Param("maxQuantity") int maxQuantity);
}

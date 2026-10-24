package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.ProductSku;
import org.apache.ibatis.annotations.Param;

/**
 * 商品 SKU Mapper。
 *
 * <p>库存增减刻意写成<b>带条件的单条 UPDATE</b>（见 {@code ProductSkuMapper.xml}），
 * 而不是「先 selectById 查库存，再 updateById 写回」：
 * 后者是两个独立语句，两条并发请求会读到同一个库存值（都读到 1），
 * 各自判断「够扣」后分别写入 0 —— 卖出一件商品却扣了两次库存，即经典的<b>超卖</b>。
 * 把判断条件放进 UPDATE 的 WHERE 里，扣减与判断由数据库在同一条语句内原子完成。
 *
 * @author Tom
 */
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /**
     * 原子扣减库存；库存不足时不更新任何行。
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量，调用方需保证为正数
     * @return 受影响行数。{@code 0} 表示库存不足（或 SKU 不存在/已删除），
     *         调用方必须据此判定失败，不能忽略返回值
     */
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 原子回补库存（订单取消/关闭时回滚）。
     *
     * @param skuId    SKU ID
     * @param quantity 回补数量
     * @return 受影响行数，{@code 0} 表示 SKU 不存在或已删除
     */
    int restoreStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);
}

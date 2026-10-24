package com.tom.tomshop.service;

import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.dto.SpuQueryDTO;
import com.tom.tomshop.dto.SpuSaveDTO;
import com.tom.tomshop.entity.ProductCategory;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.vo.SpuDetailVO;
import com.tom.tomshop.vo.SpuVO;

import java.util.Collection;
import java.util.List;

/**
 * 商品 SPU 服务。
 *
 * @author Tom
 */
public interface SpuService {

    /**
     * 「可购买的规格」载体：一次把规格、商品、分类三层都取出来并校验完。
     *
     * <p>购物车与订单都需要这段逻辑（加购要展示商品名与图，下单要拿价格与库存），
     * 把它收敛到商品域自己的服务里，避免两个上层模块各写一份、各自漏掉一层校验。
     *
     * <p>用 record 而非新建一个 VO 类：它是服务间的内部传递结构，不对外暴露，
     * 三个字段不可变即可，不需要 getter/setter 样板。
     */
    record PurchasableSku(ProductSku sku, ProductSpu spu, ProductCategory category) {
    }

    /**
     * 商品分页查询。
     *
     * <p>按分类过滤时<b>会包含该分类的所有子孙分类</b>：用「手机数码」筛选却查不到
     * 挂在「手机」下的商品，是用户一眼就能发现的错误。
     */
    PageResult<SpuVO> page(SpuQueryDTO query);

    /**
     * 商品详情（含 SKU 列表）。
     *
     * @throws com.tom.tomshop.exception.BusinessException 商品不存在时抛 2001
     */
    SpuDetailVO detail(Long id);

    /**
     * 新增商品（含 SKU）。展示价由 SKU 最低价自动算出。
     *
     * @return 新商品 ID
     */
    Long create(SpuSaveDTO dto);

    /**
     * 更新商品（含 SKU 全量覆盖）。
     *
     * <p>SKU 的增删改按「带 id 则更新、不带则新增、库中有而本次未出现则逻辑删除」处理。
     */
    void update(Long id, SpuSaveDTO dto);

    /**
     * 上下架。
     *
     * @param status 1上架 0下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 逻辑删除商品，同时逻辑删除其全部 SKU
     * —— 留下没有归属的 SKU 会让后续按 spuId 的查询出现「孤儿规格」。
     */
    void delete(Long id);

    /**
     * 加载规格并校验其可购买性（规格、商品、所属分类三层都必须处于上架状态）。
     *
     * <p>购物车加购与订单下单共用此入口，把「能否购买」的判定收敛在商品域内。
     *
     * @param skuId 规格 ID
     * @return 规格 + 商品 + 分类
     * @throws com.tom.tomshop.exception.BusinessException 规格/商品/分类不存在或已下架
     */
    PurchasableSku requirePurchasableSku(Long skuId);

    /**
     * 批量版本的 {@link #requirePurchasableSku(Long)}，任一规格不可购买即整体失败。
     *
     * <p>「全成功或全失败」是刻意的：下单时若部分规格可买、部分不可买，
     * 静默跳过不可买的会生成一个金额与用户预期不符的订单。
     */
    List<PurchasableSku> requirePurchasableSkus(Collection<Long> skuIds);
}

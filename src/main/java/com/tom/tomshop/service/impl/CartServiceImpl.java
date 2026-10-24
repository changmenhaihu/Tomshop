package com.tom.tomshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tom.tomshop.common.constant.CartConstants;
import com.tom.tomshop.common.enums.ProductStatusEnum;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.dto.CartItemAddDTO;
import com.tom.tomshop.dto.CartSelectDTO;
import com.tom.tomshop.entity.CartItem;
import com.tom.tomshop.entity.ProductCategory;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.CartItemMapper;
import com.tom.tomshop.mapper.ProductCategoryMapper;
import com.tom.tomshop.mapper.ProductSkuMapper;
import com.tom.tomshop.mapper.ProductSpuMapper;
import com.tom.tomshop.service.CartService;
import com.tom.tomshop.service.SpuService;
import com.tom.tomshop.vo.CartItemVO;
import com.tom.tomshop.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务实现。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SpuService spuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer add(CartItemAddDTO dto, Long userId) {
        // 严格校验：规格、商品、所属分类三层都必须上架，否则抛业务异常。
        // 加购这一步的报错对用户最有价值 —— 等他填完地址再告知「商品已下架」体验很差。
        spuService.requirePurchasableSku(dto.getSkuId());

        // 此处刻意不校验库存：购物车是意向清单，不是库存占用单。
        // 若加购即锁库存，不付款的用户会把库存占死。库存在下单时原子扣减。
        cartItemMapper.upsertItem(IdWorker.getId(), userId, dto.getSkuId(),
                dto.getQuantity(), CartConstants.MAX_QUANTITY_PER_SKU);

        // 回读当前数量返回给前端，让「已加入 N 件」的提示是真实值而非本地推算 ——
        // 本次是累加还是封顶，只有库里的结果说了算。
        CartItem current = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSkuId, dto.getSkuId()));
        Integer quantity = current == null ? dto.getQuantity() : current.getQuantity();
        log.info("[加购] userId={}, skuId={}, 加购={}, 购物车内={}",
                userId, dto.getSkuId(), dto.getQuantity(), quantity);
        return quantity;
    }

    @Override
    public CartVO detail(Long userId) {
        List<CartItem> items = listByUser(userId);
        return assemble(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long itemId, Integer quantity, Long userId) {
        // 上限兜底：DTO 上的 @Max 只在 Controller 层生效，直接调服务层时不会有，
        // 因此这里再判一次，保证任何入口都无法写入超限数量。
        if (quantity == null || quantity < 1 || quantity > CartConstants.MAX_QUANTITY_PER_SKU) {
            throw new BusinessException(ResultCode.CART_QUANTITY_INVALID,
                    "购买数量需在 1 ~ " + CartConstants.MAX_QUANTITY_PER_SKU + " 之间");
        }
        requireOwnedItem(itemId, userId);

        LambdaUpdateWrapper<CartItem> wrapper = new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getId, itemId)
                // where 里再带一次 user_id：即便上面校验通过，也保证更新语句本身
                // 不可能命中他人条目（防御式写法，避免日后有人删掉上面的校验）
                .eq(CartItem::getUserId, userId)
                .set(CartItem::getQuantity, quantity);
        cartItemMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> itemIds, Long userId) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择要删除的条目");
        }
        // 按「用户 + 条目」删除：只带 itemIds 的话，构造他人条目 ID 就能删掉别人的购物车
        int removed = cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, itemIds));
        log.info("[购物车删除] userId={}, 请求={} 条, 实际={} 条", userId, itemIds.size(), removed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearSelected(Long userId) {
        int cleared = cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, CartConstants.SELECTED_YES));
        log.info("[购物车清理已勾选] userId={}, 清理={} 条", userId, cleared);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelected(CartSelectDTO dto, Long userId) {
        Integer selected = Boolean.TRUE.equals(dto.getSelected())
                ? CartConstants.SELECTED_YES : CartConstants.SELECTED_NO;

        LambdaUpdateWrapper<CartItem> wrapper = new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .set(CartItem::getSelected, selected);

        List<Long> itemIds = dto.getItemIds();
        if (itemIds != null && !itemIds.isEmpty()) {
            // 数组非空 -> 批量勾选指定条目；为空/null -> 全选/全不选（只按 user_id 过滤）
            wrapper.in(CartItem::getId, itemIds);
        }
        cartItemMapper.update(null, wrapper);
    }

    @Override
    public List<CartItem> listSelectedForCheckout(Long userId) {
        List<CartItem> selected = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, CartConstants.SELECTED_YES));
        if (selected.isEmpty()) {
            throw new BusinessException(ResultCode.CART_EMPTY, "购物车中没有已勾选的商品");
        }

        // 下单前的校验必须「严格失败」而非「标记失效」：购物车列表里标记失效是给用户看的提示，
        // 但下单时若静默跳过不可买的条目，用户会收到一个金额与自己预期不符的订单。
        for (CartItem item : selected) {
            SpuService.PurchasableSku purchasable = spuService.requirePurchasableSku(item.getSkuId());
            Integer stock = purchasable.sku().getStock();
            if (stock == null || stock < item.getQuantity()) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "库存不足：" + purchasable.spu().getName() + " / " + purchasable.sku().getSkuName()
                                + "，仅剩 " + (stock == null ? 0 : stock) + " 件");
            }
        }
        return selected;
    }

    // ==================== 内部方法 ====================

    private List<CartItem> listByUser(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                // 按加购时间倒序：最近加入的排在最前，符合「刚加购想立刻确认」的直觉
                .orderByDesc(CartItem::getCreateTime)
                .orderByDesc(CartItem::getId));
    }

    private CartItem requireOwnedItem(Long itemId, Long userId) {
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            // 统一报「不存在」而不区分「条目不存在」与「条目属于他人」：
            // 后者会泄露「这个 ID 真实存在」的信息，让人可以探测他人购物车规模。
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND,
                    "购物车条目不存在：id=" + itemId);
        }
        return item;
    }

    /**
     * 组装购物车视图：批量取商品信息 + 逐条判定可用性 + 计算汇总。
     */
    private CartVO assemble(List<CartItem> items) {
        CartVO vo = new CartVO();
        if (items.isEmpty()) {
            // 空购物车也返回结构完整的对象（各汇总值为 0），而不是 null ->
            // 前端无需为空购物车写一套分支，直接渲染空列表即可
            vo.setItemCount(0);
            vo.setTotalQuantity(0);
            vo.setSelectedQuantity(0);
            vo.setSelectedAmount(BigDecimal.ZERO);
            vo.setHasUnavailable(false);
            return vo;
        }

        Set<Long> skuIds = items.stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        Map<Long, ProductSku> skuById = loadSkuMap(skuIds);

        Set<Long> spuIds = skuById.values().stream()
                .map(ProductSku::getSpuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductSpu> spuById = loadSpuMap(spuIds);

        Set<Long> categoryIds = spuById.values().stream()
                .map(ProductSpu::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductCategory> categoryById = loadCategoryMap(categoryIds);

        List<CartItemVO> itemVOs = new ArrayList<>(items.size());
        int totalQuantity = 0;
        int selectedQuantity = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        boolean hasUnavailable = false;

        for (CartItem item : items) {
            CartItemVO itemVO = buildItemVO(item, skuById, spuById, categoryById);
            itemVOs.add(itemVO);

            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += quantity;

            boolean chosen = CartConstants.SELECTED_YES.equals(item.getSelected());
            if (chosen && Boolean.TRUE.equals(itemVO.getAvailable())) {
                selectedQuantity += quantity;
                selectedAmount = selectedAmount.add(itemVO.getSubtotal());
            }
            if (!Boolean.TRUE.equals(itemVO.getAvailable())) {
                hasUnavailable = true;
            }
        }

        vo.setItems(itemVOs);
        vo.setItemCount(itemVOs.size());
        vo.setTotalQuantity(totalQuantity);
        vo.setSelectedQuantity(selectedQuantity);
        vo.setSelectedAmount(selectedAmount);
        vo.setHasUnavailable(hasUnavailable);
        return vo;
    }

    /**
     * 把一条购物车条目 + 商品信息组装成 VO，并判定其可结算性。
     *
     * <p>失效原因按「从具体到笼统」的顺序返回第一条命中的：
     * 「规格已下架」比「商品已下架」对用户更有指向性，先报出来更有用。
     */
    private CartItemVO buildItemVO(CartItem item,
                                   Map<Long, ProductSku> skuById,
                                   Map<Long, ProductSpu> spuById,
                                   Map<Long, ProductCategory> categoryById) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setSkuId(item.getSkuId());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(item.getSelected());

        ProductSku sku = skuById.get(item.getSkuId());
        if (sku == null) {
            // 规格被物理删除，或数据被人工订正过。此时连商品名都取不到，
            // 但仍要返回条目让用户能手动删除它。
            vo.setAvailable(false);
            vo.setUnavailableReason("商品规格已失效");
            vo.setSpuName("已失效的商品");
            return vo;
        }

        vo.setSpuId(sku.getSpuId());
        vo.setSkuName(sku.getSkuName());
        vo.setSpec(sku.getSpec());
        vo.setPrice(sku.getPrice());
        vo.setStock(sku.getStock());
        // 图片优先用规格图，没有则回退商品主图 —— SKU 图缺失是常见情况，
        // 不留空白比展示占位图更自然
        vo.setImage(sku.getImage());

        ProductSpu spu = spuById.get(sku.getSpuId());
        if (spu == null) {
            vo.setAvailable(false);
            vo.setUnavailableReason("商品已下架或删除");
            vo.setSubtotal(subtotal(sku.getPrice(), item.getQuantity()));
            return vo;
        }
        vo.setSpuName(spu.getName());
        if (vo.getImage() == null) {
            vo.setImage(spu.getMainImage());
        }

        vo.setSubtotal(subtotal(sku.getPrice(), item.getQuantity()));

        if (!ProductStatusEnum.ON_SHELF.getCode().equals(sku.getStatus())) {
            vo.setAvailable(false);
            vo.setUnavailableReason("该规格已下架");
        } else if (!ProductStatusEnum.ON_SHELF.getCode().equals(spu.getStatus())) {
            vo.setAvailable(false);
            vo.setUnavailableReason("商品已下架");
        } else {
            ProductCategory category = categoryById.get(spu.getCategoryId());
            if (category == null || !ProductStatusEnum.ON_SHELF.getCode().equals(category.getStatus())) {
                vo.setAvailable(false);
                vo.setUnavailableReason("商品所属分类已下架");
            } else {
                int stock = sku.getStock() == null ? 0 : sku.getStock();
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                if (stock < quantity) {
                    vo.setAvailable(false);
                    vo.setUnavailableReason(stock <= 0 ? "商品已售罄" : "库存不足，仅剩 " + stock + " 件");
                } else {
                    vo.setAvailable(true);
                }
            }
        }
        return vo;
    }

    private BigDecimal subtotal(BigDecimal price, Integer quantity) {
        if (price == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 批量加载规格。先探空再查：{@code selectBatchIds} 收到空集合会拼出非法的
     * {@code IN ()} 语句，空集合必须提前拦住。
     */
    private Map<Long, ProductSku> loadSkuMap(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, Function.identity()));
    }

    private Map<Long, ProductSpu> loadSpuMap(Collection<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return spuMapper.selectBatchIds(spuIds).stream()
                .collect(Collectors.toMap(ProductSpu::getId, Function.identity()));
    }

    private Map<Long, ProductCategory> loadCategoryMap(Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
    }
}

package com.tom.tomshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tom.tomshop.cache.CacheKeys;
import com.tom.tomshop.cache.CachePolicy;
import com.tom.tomshop.cache.MultiLevelCache;
import com.tom.tomshop.common.enums.ProductStatusEnum;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.convert.SkuConverter;
import com.tom.tomshop.convert.SpuConverter;
import com.tom.tomshop.dto.SkuSaveDTO;
import com.tom.tomshop.dto.SpuQueryDTO;
import com.tom.tomshop.dto.SpuSaveDTO;
import com.tom.tomshop.entity.ProductCategory;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.ProductCategoryMapper;
import com.tom.tomshop.mapper.ProductSkuMapper;
import com.tom.tomshop.mapper.ProductSpuMapper;
import com.tom.tomshop.service.SpuService;
import com.tom.tomshop.utils.PageUtils;
import com.tom.tomshop.vo.SkuVO;
import com.tom.tomshop.vo.SpuDetailVO;
import com.tom.tomshop.vo.SpuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品 SPU 服务实现。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    /**
     * 商品详情缓存策略：L1 60 秒 / L2 5 分钟。
     *
     * <p>TTL 刻意比其他数据短：详情里带 SKU 库存，而下单扣库存不会逐个失效商品详情
     * （那会让一次下单触发大量缓存删除）。5 分钟内库存显示可能偏乐观，
     * <b>真正的库存校验发生在加购与下单路径上，且那条路径不走缓存</b>。
     */
    private static final CachePolicy DETAIL_CACHE_POLICY = CachePolicy.of(60, 300);

    /**
     * 分类父子映射缓存策略：L1 5 分钟 / L2 30 分钟，与分类树保持一致。
     */
    private static final CachePolicy CATEGORY_MAP_CACHE_POLICY = CachePolicy.of(300, 1800);

    private static final TypeReference<SpuDetailVO> DETAIL_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<Map<Long, List<Long>>> CATEGORY_CHILDREN_TYPE = new TypeReference<>() {
    };

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SpuConverter spuConverter;
    private final SkuConverter skuConverter;
    private final MultiLevelCache cache;

    @Override
    public PageResult<SpuVO> page(SpuQueryDTO query) {
        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();

        // 分类过滤：展开成「本分类 + 全部子孙分类」的 id 集合后用 IN 查询
        if (query.getCategoryId() != null) {
            Set<Long> categoryIds = collectCategoryIds(query.getCategoryId());
            wrapper.in(ProductSpu::getCategoryId, categoryIds);
        }

        // 关键字同时匹配名称与副标题。必须用 and(...) 包一层，
        // 否则 `like(name).or().like(subtitle)` 会与前面的 in 条件形成
        // `A AND B OR C` 的优先级错乱，把其它过滤条件全部绕过。
        String keyword = query.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ProductSpu::getName, keyword)
                    .or()
                    .like(ProductSpu::getSubtitle, keyword));
        }

        wrapper.ge(query.getMinPrice() != null, ProductSpu::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, ProductSpu::getPrice, query.getMaxPrice())
                .eq(query.getStatus() != null, ProductSpu::getStatus, query.getStatus());

        // 排序字段白名单：未命中白名单则退回默认排序（新品在前）
        String orderColumn = query.resolveSortField("price", "sales", "createTime");
        switch (orderColumn == null ? "" : orderColumn) {
            case "price" -> wrapper.orderBy(true, query.isAscending(), ProductSpu::getPrice);
            case "sales" -> wrapper.orderBy(true, query.isAscending(), ProductSpu::getSales);
            case "createTime" -> wrapper.orderBy(true, query.isAscending(), ProductSpu::getCreateTime);
            default -> wrapper.orderByDesc(ProductSpu::getCreateTime).orderByDesc(ProductSpu::getId);
        }

        IPage<ProductSpu> page = spuMapper.selectPage(
                new Page<>(query.getPageNumValue(), query.getPageSizeValue()), wrapper);
        return PageUtils.toPageResult(page, spuConverter::toVO);
    }

    @Override
    public SpuDetailVO detail(Long id) {
        // 回源函数返回 null 代表「商品不存在」，由缓存层写入空值标记，
        // 避免爬虫/恶意请求用一个不存在的 ID 反复打穿到数据库
        SpuDetailVO detail = cache.getOrLoad(CacheKeys.productDetail(id), DETAIL_TYPE,
                () -> loadDetail(id), DETAIL_CACHE_POLICY);
        if (detail == null) {
            throw new BusinessException(ResultCode.SPU_NOT_FOUND, "商品不存在：id=" + id);
        }
        return detail;
    }

    /**
     * 商品详情回源：SPU（含分类名）+ SKU 列表。
     */
    private SpuDetailVO loadDetail(Long id) {
        // 走自定义 ResultMap（ProductSpuMapper.xml）：一次带出 SPU 与所属分类名称
        SpuDetailVO detail = spuMapper.selectSpuDetail(id);
        if (detail == null) {
            return null;
        }
        // SKU 是一对多，单独查一次避免 SQL 行膨胀（见 SpuDetailVO 类注释）
        detail.setSkuList(loadSkuList(id));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SpuSaveDTO dto) {
        requireCategory(dto.getCategoryId());

        ProductSpu spu = spuConverter.toEntity(dto);
        // 展示价是 SKU 最低价的冗余字段，必须在落库前算好，
        // 否则列表页会显示 0 元，直到有人更新商品才会修正。
        spu.setPrice(minSkuPrice(dto.getSkuList()));
        spu.setSales(0);
        spuMapper.insert(spu);

        insertSkus(spu.getId(), dto.getSkuList());
        log.info("[商品新增] id={}, name={}, 分类={}, SKU 数={}",
                spu.getId(), spu.getName(), dto.getCategoryId(), dto.getSkuList().size());
        return spu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SpuSaveDTO dto) {
        ProductSpu exist = requireSpu(id);
        requireCategory(dto.getCategoryId());

        ProductSpu update = spuConverter.toEntity(dto);
        update.setId(exist.getId());
        // 展示价随 SKU 变化重算；sales 不在 DTO 里，update 时保持 null ->
        // 被 update-strategy=not_null 忽略，不会把已有销量冲掉
        update.setPrice(minSkuPrice(dto.getSkuList()));
        spuMapper.updateById(update);

        int affected = syncSkus(id, dto.getSkuList());
        cache.evictAfterCommit(CacheKeys.productDetail(id));
        log.info("[商品更新] id={}, name={}, SKU 变更={} 条", id, dto.getName(), affected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        requireSpu(id);
        ProductSpu update = new ProductSpu();
        update.setId(id);
        update.setStatus(status);
        spuMapper.updateById(update);
        cache.evictAfterCommit(CacheKeys.productDetail(id));
        log.info("[商品状态变更] id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSpu(id);
        spuMapper.deleteById(id);
        // SKU 一并逻辑删除，避免产生查不到商品归属的孤儿规格
        skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, id));
        cache.evictAfterCommit(CacheKeys.productDetail(id));
        log.info("[商品删除] id={}", id);
    }

    // ==================== 内部方法 ====================

    /**
     * 按 spuId 加载 SKU 并转 VO，按价格升序 —— 前端规格选择器通常以此顺序展示。
     */
    private List<SkuVO> loadSkuList(Long spuId) {
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSpuId, spuId)
                        .orderByAsc(ProductSku::getPrice)
                        .orderByAsc(ProductSku::getId));
        return skuConverter.toVOList(skus);
    }

    private void insertSkus(Long spuId, List<SkuSaveDTO> skuList) {
        for (SkuSaveDTO skuDto : skuList) {
            ProductSku sku = skuConverter.toEntity(skuDto);
            sku.setSpuId(spuId);
            // 显式置 0：不依赖数据库 DEFAULT，避免后续调整全局 insert 策略时行为漂移
            sku.setSales(0);
            // 会话传入的 id 在新增语义下无意义，清掉让 MyBatis-Plus 生成雪花 ID
            sku.setId(null);
            skuMapper.insert(sku);
        }
    }

    /**
     * SKU 差异同步，返回发生变更（新增 + 更新 + 删除）的条数。
     *
     * <p>语义是「全量覆盖」：以入参列表为商品的最终形态，与库中现有 SKU 比对后决定动作。
     */
    private int syncSkus(Long spuId, List<SkuSaveDTO> skuList) {
        List<ProductSku> existing = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, spuId));
        Map<Long, ProductSku> existingById = existing.stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));

        int changed = 0;
        Set<Long> incomingIds = new HashSet<>();
        for (SkuSaveDTO skuDto : skuList) {
            if (skuDto.getId() == null) {
                // 新增
                ProductSku sku = skuConverter.toEntity(skuDto);
                sku.setSpuId(spuId);
                sku.setSales(0);
                skuMapper.insert(sku);
                changed++;
                continue;
            }

            ProductSku current = existingById.get(skuDto.getId());
            if (current == null) {
                // 关键校验：带 id 但该 SKU 不属于本商品。
                // 不拦的话，恶意请求可以借更新 A 商品的机会改写 B 商品的规格价格。
                throw new BusinessException(ResultCode.SKU_NOT_FOUND,
                        "规格不属于该商品：spuId=" + spuId + ", skuId=" + skuDto.getId());
            }

            ProductSku update = skuConverter.toEntity(skuDto);
            update.setSpuId(spuId);
            // 库中已有销量，不能因为这次更新被置空或归零
            update.setSales(null);
            skuMapper.updateById(update);
            incomingIds.add(skuDto.getId());
            changed++;
        }

        // 库中存在但本次未提交的 -> 逻辑删除
        for (ProductSku orphan : existing) {
            if (!incomingIds.contains(orphan.getId())) {
                skuMapper.deleteById(orphan.getId());
                changed++;
            }
        }
        return changed;
    }

    /**
     * 取 SKU 列表中的最低价作为商品展示价。
     *
     * <p>入参列表已被 {@code @NotEmpty} 保证非空，因此 {@code min} 必有值；
     * 若出现空值说明校验被绕过，此处抛出参数异常而非返回 0，避免以 0 元价格落库。
     */
    private BigDecimal minSkuPrice(List<SkuSaveDTO> skuList) {
        return skuList.stream()
                .map(SkuSaveDTO::getPrice)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_INVALID, "商品至少需要一个规格（SKU）"));
    }

    /**
     * 收集某分类及其全部子孙分类的 id（含自身）。
     *
     * <p>分类表数据量很小（通常几十条），因此一次性把 id/parentId 读进内存做广度优先展开，
     * 而不是递归发 SQL —— 后者在分类层级加深时会变成 N 次查询，且容易写出死循环。
     */
    private Set<Long> collectCategoryIds(Long rootId) {
        // 父子映射走 L1+L2 缓存：它只有一条键、几十个条目，却在每次带分类筛选的商品分页中
        // 都要用到。原始实现是「每个请求全表扫一遍分类表」，商品浏览高峰期这部分是纯浪费。
        Map<Long, List<Long>> childrenByParent = cache.getOrLoad(
                CacheKeys.CATEGORY_CHILDREN_MAP, CATEGORY_CHILDREN_TYPE,
                this::loadCategoryChildrenMap, CATEGORY_MAP_CACHE_POLICY);
        if (childrenByParent == null) {
            childrenByParent = Map.of();
        }

        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            // visited 判断放在出队处而非入队处：同一节点可能被多次入队，
            // 这里保证它只被展开一次，也天然防止父子关系成环时的死循环
            if (!result.add(current)) {
                continue;
            }
            List<Long> children = childrenByParent.get(current);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return result.isEmpty() ? Set.of(rootId) : result;
    }

    /**
     * 回源：把分类表读成「父 ID → 子 ID 列表」。
     *
     * <p>只取 id / parentId 两列，且只取未删除的行（{@code @TableLogic} 自动追加 {@code deleted = 0}）。
     * 这里不过滤 {@code status}：分类下架后，其子分类仍应参与商品筛选的祖先关系计算。
     */
    private Map<Long, List<Long>> loadCategoryChildrenMap() {
        List<ProductCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .select(ProductCategory::getId, ProductCategory::getParentId));
        return all.stream()
                // parentId 为 null 时按根分类（0）归组：Collectors.groupingBy 的分组函数返回 null 会直接抛 NPE
                .collect(Collectors.groupingBy(
                        category -> category.getParentId() == null ? 0L : category.getParentId(),
                        Collectors.mapping(ProductCategory::getId, Collectors.toList())));
    }

    private ProductSpu requireSpu(Long id) {
        ProductSpu spu = spuMapper.selectById(id);
        if (spu == null) {
            throw new BusinessException(ResultCode.SPU_NOT_FOUND, "商品不存在：id=" + id);
        }
        return spu;
    }

    private ProductCategory requireCategory(Long categoryId) {
        ProductCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "商品分类不存在：id=" + categoryId);
        }
        return category;
    }

    @Override
    public PurchasableSku requirePurchasableSku(Long skuId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.SKU_NOT_FOUND, "商品规格不存在：id=" + skuId);
        }

        ProductSpu spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null) {
            // 理论上不该发生（删除商品时会一并删 SKU），但数据被手工订正过时会出现，
            // 这里显式兜底，避免后续对 spu 解引用时抛 NPE 变成 5000 系统异常
            throw new BusinessException(ResultCode.SPU_NOT_FOUND, "规格所属商品不存在：skuId=" + skuId);
        }

        // 三层上架状态都要校验：规格上架不等于商品上架，商品上架也可能挂在已下架的分类下。
        // 运营下架一个分类时期望的是「该分类下所有商品都买不到」，漏掉分类层就实现不了。
        if (!ProductStatusEnum.ON_SHELF.getCode().equals(sku.getStatus())) {
            throw new BusinessException(ResultCode.SPU_OFF_SHELF, "规格已下架：" + sku.getSkuName());
        }
        if (!ProductStatusEnum.ON_SHELF.getCode().equals(spu.getStatus())) {
            throw new BusinessException(ResultCode.SPU_OFF_SHELF, "商品已下架：" + spu.getName());
        }

        ProductCategory category = categoryMapper.selectById(spu.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "商品分类不存在：id=" + spu.getCategoryId());
        }
        if (!ProductStatusEnum.ON_SHELF.getCode().equals(category.getStatus())) {
            throw new BusinessException(ResultCode.SPU_OFF_SHELF, "商品所属分类已下架：" + category.getName());
        }

        return new PurchasableSku(sku, spu, category);
    }

    @Override
    public List<PurchasableSku> requirePurchasableSkus(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        // 逐个加载而非批量 IN 查询：批量虽少几次 SQL，但拿到结果后仍要逐条判断
        // 「哪一个缺失/下架」才能给出精确的报错文案，反而更绕。
        // 加购与下单都是低频操作，这里优先选可读性与准确的错误信息。
        List<PurchasableSku> result = new ArrayList<>(skuIds.size());
        for (Long skuId : skuIds) {
            result.add(requirePurchasableSku(skuId));
        }
        return result;
    }
}

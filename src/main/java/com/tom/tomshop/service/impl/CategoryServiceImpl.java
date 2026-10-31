package com.tom.tomshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tom.tomshop.cache.CacheKeys;
import com.tom.tomshop.cache.CachePolicy;
import com.tom.tomshop.cache.MultiLevelCache;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.convert.CategoryConverter;
import com.tom.tomshop.dto.CategoryQueryDTO;
import com.tom.tomshop.dto.CategorySaveDTO;
import com.tom.tomshop.entity.ProductCategory;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.ProductCategoryMapper;
import com.tom.tomshop.service.CategoryService;
import com.tom.tomshop.utils.PageUtils;
import com.tom.tomshop.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现。
 *
 * <p>业务逻辑全部在 Service 层，Controller 只做参数校验与转发。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /**
     * 分类树缓存策略：L1 5 分钟 / L2 30 分钟。
     *
     * <p>分类是典型的「读多写极少」数据，但写操作必须走 {@code evictAfterCommit}：
     * 依赖 TTL 自然过期会让运营改完分类后，前台最长半小时看不到变化。
     */
    private static final CachePolicy TREE_CACHE_POLICY = CachePolicy.of(300, 1800);

    private static final TypeReference<List<CategoryVO>> TREE_TYPE = new TypeReference<>() {
    };

    private final ProductCategoryMapper categoryMapper;
    private final CategoryConverter categoryConverter;
    private final MultiLevelCache cache;

    @Override
    public List<CategoryVO> tree() {
        return cache.getOrLoad(CacheKeys.CATEGORY_TREE, TREE_TYPE, this::loadTree, TREE_CACHE_POLICY);
    }

    /**
     * 回源：查上架分类并组装成树。
     *
     * <p>返回的可变 {@code List} 会被缓存在 Redis 中，调用方（Controller → Jackson 序列化）
     * 只读不写，因此不做防御性拷贝。
     */
    private List<CategoryVO> loadTree() {
        List<ProductCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getStatus, 1)
                        .orderByAsc(ProductCategory::getSort)
                        .orderByAsc(ProductCategory::getId));
        List<CategoryVO> vos = categoryConverter.toVOList(all);
        return buildTree(vos);
    }

    @Override
    public PageResult<CategoryVO> page(CategoryQueryDTO query) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getParentId() != null, ProductCategory::getParentId, query.getParentId())
                .eq(query.getStatus() != null, ProductCategory::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getName()), ProductCategory::getName, query.getName());

        // 排序字段白名单校验（防止 ORDER BY 注入），命中白名单才应用，否则用默认排序
        String orderColumn = query.resolveSortField("sort", "createTime");
        if ("sort".equals(orderColumn)) {
            wrapper.orderBy(true, query.isAscending(), ProductCategory::getSort);
        } else if ("createTime".equals(orderColumn)) {
            wrapper.orderBy(true, query.isAscending(), ProductCategory::getCreateTime);
        } else {
            wrapper.orderByAsc(ProductCategory::getSort).orderByAsc(ProductCategory::getId);
        }

        IPage<ProductCategory> page = categoryMapper.selectPage(
                new Page<>(query.getPageNumValue(), query.getPageSizeValue()), wrapper);
        return PageUtils.toPageResult(page, categoryConverter::toVO);
    }

    @Override
    public CategoryVO detail(Long id) {
        return categoryConverter.toVO(requireCategory(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveDTO dto) {
        ProductCategory entity = categoryConverter.toEntity(dto);
        categoryMapper.insert(entity);
        evictCategoryCaches();
        log.info("[分类新增] id={}, name={}, parentId={}", entity.getId(), entity.getName(), entity.getParentId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveDTO dto) {
        ProductCategory exist = requireCategory(id);
        // 转换后强制回填主键，避免新增语义；updateById 只更新非 null 字段（update-strategy=not_null）
        ProductCategory update = categoryConverter.toEntity(dto);
        update.setId(exist.getId());
        categoryMapper.updateById(update);
        evictCategoryCaches();
        log.info("[分类更新] id={}, name={}", id, dto.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 先确认存在，避免对不存在的 id 静默「删除成功」
        requireCategory(id);
        // @TableLogic 会把 removeById 改写为 UPDATE deleted=1（逻辑删除）
        categoryMapper.deleteById(id);
        evictCategoryCaches();
        log.info("[分类删除] id={}", id);
    }

    /**
     * 失效分类相关缓存。
     *
     * <p>必须同时清两个键：分类树用于导航展示，父子映射用于商品分页的分类展开，
     * 漏掉任何一个都会出现「导航更新了、商品筛选还是旧范围」的割裂现象。
     *
     * <p>用 {@code evictAfterCommit} 而非直接删：本方法运行在事务中，
     * 提交前删缓存会让并发读把尚未提交的旧值重新写回。
     */
    private void evictCategoryCaches() {
        cache.evictAfterCommit(CacheKeys.CATEGORY_TREE, CacheKeys.CATEGORY_CHILDREN_MAP);
    }

    /**
     * 查询分类，不存在时抛出可预期的业务异常。
     */
    private ProductCategory requireCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在：id=" + id);
        }
        return category;
    }

    /**
     * 把扁平的分类列表组装为树。
     *
     * <p>思路：按 parentId 分组，root 为「parentId=0」或「父节点已不在结果集中」的节点
     * （后者兼容只查子集时仍能正确成树）。children 挂载顺序保持与入参一致（已按 sort 排好）。
     */
    private List<CategoryVO> buildTree(List<CategoryVO> flat) {
        if (flat.isEmpty()) {
            return List.of();
        }
        // 结果集中出现的所有节点 id，用于判断「某节点的父节点是否也在结果集中」
        Set<Long> ids = flat.stream().map(CategoryVO::getId).collect(Collectors.toSet());
        // key = 父节点 id，value = 该父节点下的子节点列表（groupingBy 已天然完成分组）
        Map<Long, List<CategoryVO>> byParent = flat.stream()
                .collect(Collectors.groupingBy(CategoryVO::getParentId, Collectors.toList()));

        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO node : flat) {
            Long parentId = node.getParentId();
            // parentId 为空或 0 是根；父节点不在结果集内也视为根，避免孤立节点被丢弃
            if (parentId == null || parentId == 0L || !ids.contains(parentId)) {
                roots.add(node);
            }
        }
        // 递归挂载 children
        attachChildren(roots, byParent);
        return roots;
    }

    private void attachChildren(List<CategoryVO> nodes, Map<Long, List<CategoryVO>> byParent) {
        for (CategoryVO node : nodes) {
            List<CategoryVO> children = byParent.get(node.getId());
            if (children != null && !children.isEmpty()) {
                node.setChildren(children);
                attachChildren(children, byParent);
            }
        }
    }
}

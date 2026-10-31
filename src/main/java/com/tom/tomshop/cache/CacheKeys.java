package com.tom.tomshop.cache;

/**
 * 缓存键的唯一来源。
 *
 * <p>键散落在各个 Service 里手拼字符串，是缓存类 bug 的常见来源：
 * 写入方拼 {@code product:detail:1}、失效方拼 {@code product:detail_1}，
 * 两边都不报错，只是永远命中不了。统一收口到本类，编译期就能发现拼错。
 *
 * <p>键里<b>不含</b> Redis 前缀（{@code tomshop:cache:v1:}），前缀由
 * {@link MultiLevelCache} 统一拼接 —— 前缀的作用是整体切换缓存代际，
 * 不应该让业务代码感知。
 *
 * @author Tom
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 分类树（只含上架分类，带 children 层级） */
    public static final String CATEGORY_TREE = "category:tree";

    /** 分类父子映射（id → 子 id 列表），商品分页按分类树展开时使用 */
    public static final String CATEGORY_CHILDREN_MAP = "category:children-map";

    /** 商品详情（含 SKU 列表） */
    public static String productDetail(Long spuId) {
        return "product:detail:" + spuId;
    }
}

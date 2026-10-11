package com.tom.tomshop.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tom.tomshop.common.result.PageResult;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果转换工具。
 *
 * <p>职责边界：MyBatis-Plus 的 {@code Page<Entity>} 绝不允许直接返回给前端（会泄露表结构、
 * 带出逻辑删除标记与乐观锁版本号），必须在此转换成 {@code PageResult<VO>}。
 * 放在 utils 而非 {@code PageResult} 中，是为了让 {@code common.result} 保持与 ORM 框架解耦。
 *
 * @author Tom
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 分页 + 元素转换：把 {@code IPage<Entity>} 转成 {@code PageResult<VO>}。
     *
     * <pre>{@code
     * IPage<Product> page = productMapper.selectPage(new Page<>(...), wrapper);
     * return PageUtils.toPageResult(page, productConverter::toVO);
     * }</pre>
     *
     * @param page      MyBatis-Plus 分页结果
     * @param converter 单元素转换函数，通常传 MapStruct 生成的转换器方法引用
     */
    public static <E, V> PageResult<V> toPageResult(IPage<E> page, Function<E, V> converter) {
        List<V> records = page.getRecords() == null
                ? List.of()
                : page.getRecords().stream().map(converter).collect(Collectors.toList());
        return toPageResult(page, records);
    }

    /**
     * 分页 + 已转换好的列表：适用于元素转换需要批量预处理的场景
     * （例如避免 N+1 查询，先批量查出 SKU 再组装 VO）。
     *
     * @param page    任意分页结果，仅取分页元信息
     * @param records 已完成转换的当前页数据
     */
    public static <V> PageResult<V> toPageResult(IPage<?> page, List<V> records) {
        return new PageResult<>(
                page.getTotal(),
                page.getPages(),
                page.getCurrent(),
                page.getSize(),
                records == null ? List.of() : records);
    }
}

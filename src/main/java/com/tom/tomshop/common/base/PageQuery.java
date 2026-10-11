package com.tom.tomshop.common.base;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 分页查询入参基类。各模块的分页 DTO 继承本类并追加自己的过滤条件。
 *
 * <p><b>排序字段安全约束（重要）</b>：{@link #sortField} 最终会被拼进 {@code ORDER BY}，
 * 属于 SQL 注入高危点。禁止把它直接交给 MyBatis-Plus 的 {@code OrderItem}，
 * 必须经 {@link #resolveSortField(String...)} 做白名单校验：
 * <pre>{@code
 * String orderColumn = query.resolveSortField("create_time", "price", "sales");
 * if (orderColumn != null) {
 *     wrapper.orderBy(true, query.isAscending(), orderColumn);
 * }
 * }</pre>
 * 白名单未命中时该方法返回 {@code null}，调用方应退化为默认排序，而非报错。
 *
 * @author Tom
 */
@Data
@Schema(description = "分页查询基础参数")
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 " + MAX_PAGE_SIZE)
    @Schema(description = "每页条数，上限 " + MAX_PAGE_SIZE, example = "10", defaultValue = "10")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    @Schema(description = "排序字段，必须落在业务白名单内，否则忽略", example = "create_time")
    private String sortField;

    @Schema(description = "是否升序，默认 true", example = "true", defaultValue = "true")
    private Boolean asc = Boolean.TRUE;

    /**
     * 校验排序字段是否落在白名单内。命中返回原字段名，未命中或未传返回 {@code null}。
     *
     * @param allowedFields 该接口允许排序的列名白名单
     */
    public String resolveSortField(String... allowedFields) {
        if (sortField == null || sortField.isBlank() || allowedFields == null || allowedFields.length == 0) {
            return null;
        }
        String candidate = sortField.trim();
        return Arrays.stream(allowedFields)
                .filter(allowed -> allowed.equalsIgnoreCase(candidate))
                .findFirst()
                .orElse(null);
    }

    /** 参与分页的页码，做了空值与下界兜底，供 Service 直接使用 */
    @Schema(hidden = true)
    public long getPageNumValue() {
        return (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    /** 参与分页的每页条数，做了空值与上界兜底（防止绕过 @Max 的编程式调用） */
    @Schema(hidden = true)
    public long getPageSizeValue() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 排序方向，{@code asc} 为 null 时按升序处理 */
    @Schema(hidden = true)
    public boolean isAscending() {
        return !Boolean.FALSE.equals(asc);
    }
}

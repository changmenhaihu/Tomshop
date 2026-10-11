package com.tom.tomshop.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结果。
 *
 * <p><b>类型约定</b>：计数类字段刻意使用基本类型 {@code long} / {@code int}，
 * 使其在 JSON 中保持数字。因为 JacksonConfig 会把包装类型 {@code Long} 序列化为字符串
 * （保护雪花 ID 精度），若此处用 {@code Long}，前端拿到的 total 会变成 {@code "100"} 而非常规数字。
 *
 * <p>Entity → VO 的转换请使用 {@code PageUtils.toPageResult(...)}，不要在 Controller 中手工拼装。
 *
 * @param <T> 列表元素类型（对外一律是 VO）
 * @author Tom
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "总记录数", example = "128")
    private long total;

    @Schema(description = "总页数", example = "13")
    private long pages;

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private long current;

    @Schema(description = "每页条数", example = "10")
    private long size;

    @Schema(description = "当前页数据列表")
    private List<T> records;

    /** 空分页结果，用于查询无命中但需保持响应结构一致的场景 */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, 0L, 1L, 0L, Collections.emptyList());
    }
}

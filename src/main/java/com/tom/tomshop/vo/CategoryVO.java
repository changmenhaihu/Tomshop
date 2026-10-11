package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品分类对外视图。
 *
 * <p>与实体 {@code ProductCategory} 的差异：不含 {@code deleted} / {@code version} 等内部字段，
 * 通过 {@code children} 承载树形结构（由 Service 组装，非 MapStruct 转换）。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品分类视图")
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分类 ID（字符串，雪花 ID）", example = "11")
    private Long id;

    @Schema(description = "父分类 ID，0 表示根分类", example = "1")
    private Long parentId;

    @Schema(description = "分类名称", example = "手机")
    private String name;

    @Schema(description = "层级：1一级 2二级 3三级", example = "2")
    private Integer level;

    @Schema(description = "同级排序", example = "1")
    private Integer sort;

    @Schema(description = "分类图标 URL")
    private String icon;

    @Schema(description = "状态：1上架 0下架", example = "1")
    private Integer status;

    @Schema(description = "创建时间", example = "2026-10-09 12:00:00")
    private LocalDateTime createTime;

    @Schema(description = "子分类列表（树形结构）")
    private List<CategoryVO> children;
}

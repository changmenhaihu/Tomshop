package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品分类新增/更新入参。
 *
 * <p>新增与更新共用一个 DTO：新增时主键由雪花算法生成，更新时主键走路径参数 {@code /{id}}，
 * 因此 DTO 内不含 id。这样做减少重复类，代价是更新语义为「传什么改什么」。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品分类新增/更新入参")
public class CategorySaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "父分类 ID 不能为空")
    @Schema(description = "父分类 ID，0 表示根分类", example = "0")
    private Long parentId;

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称", example = "手机")
    private String name;

    @NotNull(message = "分类层级不能为空")
    @Min(value = 1, message = "分类层级最小为 1")
    @Max(value = 3, message = "分类层级最大为 3")
    @Schema(description = "层级：1一级 2二级 3三级", example = "2")
    private Integer level;

    @Schema(description = "同级排序，值越小越靠前", example = "1", defaultValue = "0")
    private Integer sort;

    @Schema(description = "分类图标 URL", example = "https://example.com/icon.png")
    private String icon;

    @Schema(description = "状态：1上架 0下架", example = "1", defaultValue = "1")
    private Integer status;
}

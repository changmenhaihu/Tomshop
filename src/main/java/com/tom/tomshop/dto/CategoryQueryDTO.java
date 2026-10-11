package com.tom.tomshop.dto;

import com.tom.tomshop.common.base.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类分页查询入参。
 *
 * <p>继承 {@link PageQuery} 获得 pageNum / pageSize / sortField 等基础分页能力，
 * 追加业务过滤条件。排序字段白名单：{@code sort}、{@code createTime}。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商品分类分页查询入参")
public class CategoryQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "按父分类过滤，不传则查全部", example = "1")
    private Long parentId;

    @Schema(description = "按状态过滤：1上架 0下架，不传则查全部", example = "1")
    private Integer status;

    @Schema(description = "分类名称模糊匹配", example = "手机")
    private String name;
}

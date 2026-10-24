package com.tom.tomshop.dto;

import com.tom.tomshop.common.base.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品分页查询入参。
 *
 * <p>排序字段白名单：{@code price}、{@code sales}、{@code createTime}。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "商品分页查询入参")
public class SpuQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;

    @Schema(description = "按分类过滤（精确匹配），不传则查全部", example = "11")
    private Long categoryId;

    @Schema(description = "关键字，同时模糊匹配商品名称与副标题", example = "手机")
    private String keyword;

    @Schema(description = "价格下限（含）", example = "1000.00")
    private BigDecimal minPrice;

    @Schema(description = "价格上限（含）", example = "6000.00")
    private BigDecimal maxPrice;

    @Schema(description = "按状态过滤：1上架 0下架，不传则查全部", example = "1")
    private Integer status;
}

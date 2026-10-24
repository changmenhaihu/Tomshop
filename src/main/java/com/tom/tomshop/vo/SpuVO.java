package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SPU 列表项展示对象。
 *
 * <p>刻意<b>不包含</b> {@code skuList}：列表接口若带上 SKU，就需要为每一条 SPU 再查一次 SKU 表
 * （典型 N+1），一页 10 条商品会变成 11 次查询。SKU 属于详情数据，只在
 * {@link SpuDetailVO} 中返回。
 *
 * <p>同样刻意不包含 {@code detail}（富文本详情，字段很大），列表接口不需要。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品列表项")
public class SpuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "SPU ID", example = "1001")
    private Long id;

    @Schema(description = "所属分类 ID", example = "11")
    private Long categoryId;

    @Schema(description = "商品名称", example = "X 旗舰智能手机")
    private String name;

    @Schema(description = "副标题/卖点")
    private String subtitle;

    @Schema(description = "主图 URL")
    private String mainImage;

    @Schema(description = "展示价（取 SKU 最低价）", example = "4999.00")
    private BigDecimal price;

    @Schema(description = "累计销量", example = "1280")
    private Integer sales;

    @Schema(description = "状态：1上架 0下架", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

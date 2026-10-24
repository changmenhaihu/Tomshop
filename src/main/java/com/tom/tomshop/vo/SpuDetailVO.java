package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品 SPU 详情展示对象（对外 API 视图）。
 *
 * <p><b>为什么不再继承 {@code ProductSpu}</b>：阶段 1 为了演示 {@code autoMapping} 复用了实体，
 * 但那会把 {@code deleted}（逻辑删除标记）与 {@code version}（乐观锁版本号）一并带出到接口响应里。
 * 这两个字段属于存储层实现细节 —— 对外暴露后，前端可能依据 {@code version} 做判断，
 * 一旦后续调整并发策略（例如阶段 4 改用分布式锁而不再自增 version），就会变成破坏性变更。
 * 因此这里改为独立 POJO，只保留业务需要的字段。
 *
 * <p><b>数据来源</b>：基础字段与 {@code categoryName} 由 {@code ProductSpuMapper.xml} 的自定义
 * ResultMap（{@code LEFT JOIN product_category} 平铺多对一关联）一次查出；
 * {@code skuList} 由 Service 单独加载后回填 —— SKU 是一对多，塞进同一条 SQL 会产生行膨胀
 * （一条 SPU × N 个 SKU 会返回 N 行，SPU 字段重复 N 遍），不如拆成两次查询清晰。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品详情")
public class SpuDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "SPU ID", example = "1001")
    private Long id;

    @Schema(description = "所属分类 ID", example = "11")
    private Long categoryId;

    @Schema(description = "所属分类名称（关联查询平铺）", example = "手机")
    private String categoryName;

    @Schema(description = "商品名称", example = "X 旗舰智能手机")
    private String name;

    @Schema(description = "副标题/卖点")
    private String subtitle;

    @Schema(description = "主图 URL")
    private String mainImage;

    @Schema(description = "轮播图，JSON 数组字符串")
    private String images;

    @Schema(description = "富文本详情（HTML）")
    private String detail;

    @Schema(description = "展示价（取 SKU 最低价）", example = "4999.00")
    private BigDecimal price;

    @Schema(description = "累计销量", example = "1280")
    private Integer sales;

    @Schema(description = "状态：1上架 0下架", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "规格列表（SKU），按价格升序")
    private List<SkuVO> skuList;
}

package com.tom.tomshop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品 SPU 新增/更新入参。
 *
 * <p><b>为什么必须带 SKU 列表</b>：SPU 只描述「这是什么」，价格与库存都在 SKU 上。
 * 一个没有 SKU 的 SPU 既不能定价也不能下单，属于不可售的残缺数据。
 * 因此这里用 {@code @NotEmpty} 强制「建商品 = 建商品 + 至少一个规格」，
 * 避免库里出现一批无法售卖、只能靠人工回捞的脏数据。
 *
 * <p><b>SKU 的增删改如何表达</b>（见 {@link SkuSaveDTO#getId()}）：
 * <ul>
 *   <li>列表中带 {@code id} → 更新该 SKU（若 id 不属于本 SPU，直接拒绝）</li>
 *   <li>列表中不带 {@code id} → 新增 SKU</li>
 *   <li>库中存在但本次列表未出现 → 逻辑删除</li>
 * </ul>
 * 这是「全量覆盖式」语义，前端提交的是商品的完整形态，服务端负责算出差异。
 *
 * @author Tom
 */
@Data
@Schema(description = "商品新增/更新入参")
public class SpuSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属分类 ID", example = "11", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属分类不能为空")
    private Long categoryId;

    @Schema(description = "商品名称", example = "X 旗舰智能手机", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称长度不能超过 128")
    private String name;

    @Schema(description = "副标题/卖点", example = "骁龙旗舰芯片 / 1 亿像素")
    @Size(max = 256, message = "副标题长度不能超过 256")
    private String subtitle;

    @Schema(description = "主图 URL")
    @Size(max = 512, message = "主图 URL 长度不能超过 512")
    private String mainImage;

    @Schema(description = "轮播图，JSON 数组字符串")
    @Size(max = 2048, message = "轮播图长度不能超过 2048")
    private String images;

    @Schema(description = "富文本详情（HTML）")
    private String detail;

    @Schema(description = "状态：1上架 0下架", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品状态不能为空")
    @Min(value = 0, message = "商品状态只能是 0 或 1")
    @Max(value = 1, message = "商品状态只能是 0 或 1")
    private Integer status;

    @Schema(description = "规格列表，至少一个", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "商品至少需要一个规格（SKU）")
    @Valid
    private List<SkuSaveDTO> skuList;
}

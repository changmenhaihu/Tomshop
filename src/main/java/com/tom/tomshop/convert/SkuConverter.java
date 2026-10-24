package com.tom.tomshop.convert;

import com.tom.tomshop.dto.SkuSaveDTO;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.vo.SkuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 商品 SKU 转换器。
 *
 * @author Tom
 */
@Mapper(config = BaseConverterConfig.class)
public interface SkuConverter {

    /** Entity → VO */
    SkuVO toVO(ProductSku entity);

    /** Entity 列表 → VO 列表 */
    List<SkuVO> toVOList(List<ProductSku> entities);

    /**
     * 入参 DTO → Entity。
     *
     * <p>与 {@link SpuConverter#toEntity} 不同，此处<b>映射 {@code id}</b>：
     * 商品更新时前端会用 id 标识「这是对哪个已有规格的修改」，这正是差异比对的依据。
     * 新增场景下 DTO 不带 id，映射结果为 null，MyBatis-Plus 会自动填充雪花 ID。
     *
     * <p>{@code spuId} 由 Service 回填（可能来自 URL 路径），{@code sales} 归下单流程维护，
     * 两者都不接受前端输入。
     */
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "sales", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductSku toEntity(SkuSaveDTO dto);
}

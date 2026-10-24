package com.tom.tomshop.convert;

import com.tom.tomshop.dto.SpuSaveDTO;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.vo.SpuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 商品 SPU 转换器。
 *
 * @author Tom
 */
@Mapper(config = BaseConverterConfig.class)
public interface SpuConverter {

    /** Entity → 列表项 VO */
    SpuVO toVO(ProductSpu entity);

    /** Entity 列表 → VO 列表 */
    List<SpuVO> toVOList(List<ProductSpu> entities);

    /**
     * 入参 DTO → Entity。
     *
     * <p>显式忽略的字段都是<b>不该由前端输入决定</b>的：
     * <ul>
     *   <li>{@code id} / {@code createTime} / {@code updateTime} / {@code deleted} / {@code version}
     *       —— 存储层字段，由 MyBatis-Plus 与自动填充处理器维护；</li>
     *   <li>{@code sales} —— 累计销量只能由下单流程累加，允许前端传入等于允许刷单；</li>
     *   <li>{@code price} —— 展示价是 SKU 最低价的冗余，由 Service 根据 SKU 列表算出，
     *       前端传什么都不能信，否则会出现「列表页显示 1 元、详情页 4999 元」的价格不一致。</li>
     * </ul>
     * 不写 {@code ignore} 也能编译（MapStruct 默认只告警），但那样后续新增字段时
     * 会默默生成一份「看起来对」的映射，不如现在把边界写死。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "sales", ignore = true)
    @Mapping(target = "price", ignore = true)
    ProductSpu toEntity(SpuSaveDTO dto);
}

package com.tom.tomshop.convert;

import com.tom.tomshop.dto.CategorySaveDTO;
import com.tom.tomshop.entity.ProductCategory;
import com.tom.tomshop.vo.CategoryVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 商品分类转换器。
 *
 * <p>注意：{@code CategoryVO.children} 是树形结构，无法由 MapStruct 从单条实体推导，
 * 由 Service 组装后再通过 {@link #toVO(ProductCategory)} 逐条转换，此处不映射 children。
 *
 * @author Tom
 */
@Mapper(config = BaseConverterConfig.class)
public interface CategoryConverter {

    /** Entity → VO（children 由 Service 组装） */
    CategoryVO toVO(ProductCategory entity);

    /** Entity 列表 → VO 列表（不含树形组装） */
    List<CategoryVO> toVOList(List<ProductCategory> entities);

    /** 入参 DTO → Entity（新增用） */
    ProductCategory toEntity(CategorySaveDTO dto);
}

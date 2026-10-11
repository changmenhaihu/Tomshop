package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.vo.SpuDetailVO;
import org.apache.ibatis.annotations.Param;

/**
 * 商品 SPU Mapper。
 *
 * <p>自定义多表关联查询（ResultMap 示例）见 {@code resources/mapper/ProductSpuMapper.xml}。
 *
 * @author Tom
 */
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    /**
     * 查询 SPU 详情并关联分类名称（association 示例）。
     *
     * @param spuId SPU ID
     * @return 含分类名的 SPU 详情，未命中返回 null
     */
    SpuDetailVO selectSpuDetail(@Param("spuId") Long spuId);
}

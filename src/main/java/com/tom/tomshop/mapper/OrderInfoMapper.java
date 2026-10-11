package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.vo.OrderDetailVO;
import org.apache.ibatis.annotations.Param;

/**
 * 订单主表 Mapper。
 *
 * <p>一对多关联查询（collection 示例）见 {@code resources/mapper/OrderInfoMapper.xml}。
 *
 * @author Tom
 */
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 查询订单详情及其明细列表（collection 示例）。
     *
     * @param orderId 订单 ID
     * @return 含明细列表的订单详情，未命中返回 null
     */
    OrderDetailVO selectOrderDetail(@Param("orderId") Long orderId);
}

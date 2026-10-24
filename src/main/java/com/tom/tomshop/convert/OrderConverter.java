package com.tom.tomshop.convert;

import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.entity.OrderItem;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.OrderItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 订单转换器。
 *
 * <p>只做「同名字段搬运」，所有需要计算的字段（statusDesc、canCancel、totalQuantity）
 * 都留给 Service —— MapStruct 生成的是纯 getter/setter 调用，
 * 把枚举翻译、状态机判断塞进 {@code @AfterMapping} 会让转换器从「无脑搬运」
 * 变成「隐藏业务逻辑的地方」，日后排查「这个字段为什么是 false」会绕远路。
 *
 * @author Tom
 */
@Mapper(config = BaseConverterConfig.class)
public interface OrderConverter {

    /**
     * Entity → 订单视图。
     *
     * <p>忽略的字段分两类：
     * <ul>
     *   <li>派生于状态机的操作标识（canCancel / canDeliver / canFinish）与状态文案
     *       （statusDesc）—— 由 Service 依据 {@code OrderStatusEnum} 计算；</li>
     *   <li>聚合字段（items / totalQuantity）—— 需要额外查询明细表才能得到。</li>
     * </ul>
     */
    @Mapping(target = "statusDesc", ignore = true)
    @Mapping(target = "totalQuantity", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "canCancel", ignore = true)
    @Mapping(target = "canDeliver", ignore = true)
    @Mapping(target = "canFinish", ignore = true)
    OrderDetailVO toVO(OrderInfo entity);

    List<OrderDetailVO> toVOList(List<OrderInfo> entities);

    /** Entity → 明细 VO。字段名一一对应，无需忽略任何字段。 */
    OrderItemVO toItemVO(OrderItem entity);

    List<OrderItemVO> toItemVOList(List<OrderItem> entities);
}

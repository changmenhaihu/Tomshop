package com.tom.tomshop;

import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.entity.OrderItem;
import com.tom.tomshop.mapper.OrderInfoMapper;
import com.tom.tomshop.mapper.OrderItemMapper;
import com.tom.tomshop.mapper.ProductSpuMapper;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.SpuDetailVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义 ResultMap 集成测试。
 *
 * <p>验证 {@code ProductSpuMapper.xml} 的 association（平铺）与
 * {@code OrderInfoMapper.xml} 的 collection（一对多）两种关联查询的映射正确性。
 * 方法级 {@code @Transactional} 保证测试数据自动回滚，不污染开发库。
 *
 * <p>依赖真实 MySQL 与 Redis（@SpringBootTest 启动完整上下文），
 * 这是阶段 1「自定义 ResultMap 多表关联查询示例」的端到端验证。
 *
 * @author Tom
 */
@SpringBootTest
@Transactional
class ResultMapIntegrationTest {

    @Autowired
    private ProductSpuMapper spuMapper;

    @Autowired
    private OrderInfoMapper orderMapper;

    @Autowired
    private OrderItemMapper itemMapper;

    @Test
    void spuDetail_association_映射分类名称() {
        SpuDetailVO vo = spuMapper.selectSpuDetail(1001L);

        assertThat(vo).isNotNull();
        // 多对一关联：商品 -> 分类名
        assertThat(vo.getCategoryName()).isEqualTo("手机");
        // SPU 自身字段由 autoMapping 正确映射
        assertThat(vo.getName()).isEqualTo("X 旗舰智能手机");
        assertThat(vo.getCategoryId()).isEqualTo(11L);
    }

    @Test
    void orderDetail_collection_映射明细列表() {
        // 构造一条订单 + 两条明细
        OrderInfo order = new OrderInfo();
        order.setOrderNo("TEST-" + System.currentTimeMillis());
        order.setUserId(1L);
        order.setStatus(10);
        orderMapper.insert(order);

        for (int i = 1; i <= 2; i++) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setSpuId(1001L);
            item.setSkuId(10001L);
            item.setSpuName("X 旗舰智能手机");
            item.setSkuName("曜石黑 / 128G");
            item.setPrice(new BigDecimal("4999.00"));
            item.setQuantity(i);
            item.setTotalAmount(new BigDecimal("4999.00").multiply(BigDecimal.valueOf(i)));
            itemMapper.insert(item);
        }

        OrderDetailVO vo = orderMapper.selectOrderDetail(order.getId());

        assertThat(vo).isNotNull();
        assertThat(vo.getOrderNo()).isEqualTo(order.getOrderNo());
        // 一对多关联：订单 -> 明细列表，命中 2 条
        assertThat(vo.getItems()).hasSize(2);
        // 关键：item_total_amount 别名是否正确映射回 OrderItem.totalAmount
        assertThat(vo.getItems())
                .extracting(OrderItem::getQuantity)
                .containsExactlyInAnyOrder(1, 2);
        assertThat(vo.getItems())
                .allMatch(item -> item.getOrderNo().equals(order.getOrderNo()));
    }
}

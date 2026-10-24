package com.tom.tomshop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tom.tomshop.common.constant.CartConstants;
import com.tom.tomshop.common.enums.OrderStatusEnum;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.dto.CartItemAddDTO;
import com.tom.tomshop.dto.CartSelectDTO;
import com.tom.tomshop.dto.OrderSubmitDTO;
import com.tom.tomshop.dto.SkuSaveDTO;
import com.tom.tomshop.dto.SpuSaveDTO;
import com.tom.tomshop.entity.CartItem;
import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.entity.UserAddress;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.CartItemMapper;
import com.tom.tomshop.mapper.OrderInfoMapper;
import com.tom.tomshop.mapper.ProductSkuMapper;
import com.tom.tomshop.mapper.UserAddressMapper;
import com.tom.tomshop.service.CartService;
import com.tom.tomshop.service.OrderService;
import com.tom.tomshop.service.SpuService;
import com.tom.tomshop.vo.CartItemVO;
import com.tom.tomshop.vo.CartVO;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.OrderPreviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 购物车 + 订单 集成测试。
 *
 * <p>全部用例运行在 {@code @Transactional} 中并自动回滚，因此可以直接针对开发库的
 * 种子数据（SKU 10001/10002）做「先下单再断言库存」这类操作，不会污染真实数据。
 *
 * <p>测试用户使用 {@link #TEST_USER_ID} 这类远大于种子数据的 ID，
 * 即便某次运行因异常未能回滚，也不会与真实用户的数据混在一起。
 *
 * <p>覆盖的关键不变量（这些是电商最容易出事的地方）：
 * <ul>
 *   <li>库存与销量严格同步增减，取消订单时精确回滚，重复取消不产生「凭空多出的库存」；</li>
 *   <li>下单失败（库存不足）时整个事务回滚 —— 不留下订单，也不留下被扣掉的库存；</li>
 *   <li>购物车是用户私有数据，任何按 ID 的操作都必须带 user_id 条件；</li>
 *   <li>已下架商品在下单链路被拦截，在购物车列表中则标记失效而非静默消失。</li>
 * </ul>
 *
 * @author Tom
 */
@SpringBootTest
@Transactional
class CartOrderIntegrationTest {

    private static final Long TEST_USER_ID = 999901L;
    private static final Long OTHER_USER_ID = 999902L;

    /** 种子 SKU：单价 4999.00，库存 100 */
    private static final Long SEED_SKU_ID = 10001L;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductSkuMapper skuMapper;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private UserAddressMapper addressMapper;

    // ==================== 购物车 ====================

    @Test
    @DisplayName("同一 SKU 重复加购累加数量，而非新增一行")
    void add_sameSkuTwice_accumulatesQuantity() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, 2);
        Integer quantity = addToCart(TEST_USER_ID, SEED_SKU_ID, 3);

        assertThat(quantity).isEqualTo(5);
        assertThat(countCartItems(TEST_USER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("删除后重新加购：唯一键上的已删除行被复活，数量为新值而非累加，且不抛唯一键冲突")
    void add_afterRemove_resurrectsDeletedRow() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, 4);
        CartItem item = findCartItem(TEST_USER_ID, SEED_SKU_ID);
        cartService.remove(List.of(item.getId()), TEST_USER_ID);

        // 关键：cart_item 的唯一键 uk_user_sku 不含 deleted 列，
        // 「先查后插」的实现会在这里抛 DuplicateKeyException
        Integer quantity = addToCart(TEST_USER_ID, SEED_SKU_ID, 7);

        assertThat(quantity).isEqualTo(7);
        assertThat(item.getId()).isEqualTo(findCartItem(TEST_USER_ID, SEED_SKU_ID).getId());
        assertThat(countCartItems(TEST_USER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("加购数量在 SQL 内被原子封顶，不突破单 SKU 上限")
    void add_exceedingLimit_capsAtMax() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, CartConstants.MAX_QUANTITY_PER_SKU - 1);
        Integer quantity = addToCart(TEST_USER_ID, SEED_SKU_ID, 500);

        assertThat(quantity).isEqualTo(CartConstants.MAX_QUANTITY_PER_SKU);
    }

    @Test
    @DisplayName("购物车是用户私有数据：修改与删除他人条目按「不存在」处理")
    void cartItem_otherUser_cannotModifyOrRemove() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);
        CartItem item = findCartItem(TEST_USER_ID, SEED_SKU_ID);

        assertThatThrownBy(() -> cartService.updateQuantity(item.getId(), 99, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_ITEM_NOT_FOUND.getCode());

        // 删除不存在的条目是幂等的（不报错），但绝不能让他人数据被删掉
        cartService.remove(List.of(item.getId()), OTHER_USER_ID);
        assertThat(countCartItems(TEST_USER_ID)).isEqualTo(1);
        assertThat(findCartItem(TEST_USER_ID, SEED_SKU_ID).getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("商品下架后购物车条目标记失效但不消失，且不计入勾选合计")
    void detail_offShelfProduct_markedUnavailableAndExcludedFromAmount() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, 2);
        spuService.updateStatus(1001L, 0);

        CartVO cart = cartService.detail(TEST_USER_ID);

        assertThat(cart.getItems()).hasSize(1);
        CartItemVO item = cart.getItems().get(0);
        assertThat(item.getAvailable()).isFalse();
        assertThat(item.getUnavailableReason()).isNotBlank();
        assertThat(cart.getHasUnavailable()).isTrue();
        // 失效条目即便处于勾选状态也不能计入合计 —— 否则用户看到的金额与下单金额不一致
        assertThat(cart.getSelectedQuantity()).isZero();
        assertThat(cart.getSelectedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // 但仍然统计在总件数里，前端才能提示「3 件商品中有 2 件已失效」
        assertThat(cart.getTotalQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("勾选状态支持批量与全选，全选通过空 itemIds 表达")
    void updateSelected_supportsBatchAndSelectAll() {
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);
        addToCart(TEST_USER_ID, 10002L, 1);

        cartService.updateSelected(selectDto(null, false), TEST_USER_ID);
        assertThat(cartService.detail(TEST_USER_ID).getSelectedQuantity()).isZero();

        cartService.updateSelected(selectDto(null, true), TEST_USER_ID);
        CartVO cart = cartService.detail(TEST_USER_ID);
        assertThat(cart.getSelectedQuantity()).isEqualTo(2);
        // 价格 4999 + 5499，均为实时取价
        assertThat(cart.getSelectedAmount()).isEqualByComparingTo(new BigDecimal("10498.00"));
    }

    @Test
    @DisplayName("空购物车返回结构完整的对象，各汇总值为 0 而非 null")
    void detail_emptyCart_returnsZeroedSummary() {
        CartVO cart = cartService.detail(TEST_USER_ID);

        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getItemCount()).isZero();
        assertThat(cart.getTotalQuantity()).isZero();
        assertThat(cart.getSelectedQuantity()).isZero();
        assertThat(cart.getSelectedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cart.getHasUnavailable()).isFalse();
    }

    // ==================== 下单 ====================

    @Test
    @DisplayName("购物车下单：扣减库存、累加销量、计算金额、清空已勾选条目")
    void submit_fromCart_deductsStockAndClearsCart() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 2);
        int stockBefore = stockOf(SEED_SKU_ID);
        int salesBefore = salesOf(SEED_SKU_ID);

        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);

        assertThat(order.getOrderNo()).hasSize(24);
        assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.WAIT_PAY.getCode());
        assertThat(order.getStatusDesc()).isEqualTo("待支付");
        assertThat(order.getCanCancel()).isTrue();
        assertThat(order.getCanDeliver()).isFalse();
        // 4999 × 2 = 9998，已达包邮门槛
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9998.00"));
        assertThat(order.getFreightAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("9998.00"));
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalQuantity()).isEqualTo(2);

        // 库存与销量严格同步：扣 2 / 加 2，不存在只改其中一个的中间态
        assertThat(stockOf(SEED_SKU_ID)).isEqualTo(stockBefore - 2);
        assertThat(salesOf(SEED_SKU_ID)).isEqualTo(salesBefore + 2);
        // 已下单的条目从购物车移除，避免「下完单商品还躺在购物车里被重复下单」
        assertThat(countCartItems(TEST_USER_ID)).isZero();
    }

    @Test
    @DisplayName("未达包邮门槛时收取固定运费，应付金额 = 商品总额 + 运费")
    void submit_belowFreeFreightThreshold_chargesFlatFreight() {
        Long addressId = createAddress(TEST_USER_ID);
        Long cheapSkuId = createCheapSku(new BigDecimal("10.00"), 10);

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(2);
        dto.setAddressId(addressId);
        dto.setSkuId(cheapSkuId);
        dto.setQuantity(1);

        OrderDetailVO order = orderService.submit(dto, TEST_USER_ID);

        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(order.getFreightAmount()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    @DisplayName("刚好达到包邮门槛即免运费（用 compareTo 比较，避免 99.0 与 99.00 被判不等）")
    void submit_exactlyAtThreshold_isFreeFreight() {
        Long addressId = createAddress(TEST_USER_ID);
        Long skuId = createCheapSku(new BigDecimal("99.00"), 10);

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(2);
        dto.setAddressId(addressId);
        dto.setSkuId(skuId);
        dto.setQuantity(1);

        OrderDetailVO order = orderService.submit(dto, TEST_USER_ID);

        assertThat(order.getFreightAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getPayAmount()).isEqualByComparingTo(new BigDecimal("99.00"));
    }

    @Test
    @DisplayName("立即购买不动购物车：用户未决定要买的商品不该被清掉")
    void submit_directBuy_leavesCartUntouched() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(2);
        dto.setAddressId(addressId);
        dto.setSkuId(10002L);
        dto.setQuantity(1);

        orderService.submit(dto, TEST_USER_ID);

        assertThat(countCartItems(TEST_USER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("库存不足时下单失败，且不留下订单、不留下被扣掉的库存（事务整体回滚）")
    void submit_stockNotEnough_rollsBackEntireTransaction() {
        Long addressId = createAddress(TEST_USER_ID);
        // 库存 1，却买 5 —— 购物车校验阶段就会拦下
        Long skuId = createCheapSku(new BigDecimal("50.00"), 1);
        addToCart(TEST_USER_ID, skuId, 5);
        int stockBefore = stockOf(skuId);

        assertThatThrownBy(() -> orderService.submit(submitFromCart(addressId), TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.STOCK_NOT_ENOUGH.getCode());

        assertThat(stockOf(skuId)).isEqualTo(stockBefore);
        assertThat(countOrders(TEST_USER_ID)).isZero();
        // 下单失败不该顺手清空购物车，用户改个数量就能重试
        assertThat(countCartItems(TEST_USER_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("立即购买绕过购物车校验时，由原子扣减兜底拦截超卖")
    void submit_directBuyOverStock_blockedByAtomicDeduct() {
        Long addressId = createAddress(TEST_USER_ID);
        Long skuId = createCheapSku(new BigDecimal("50.00"), 1);
        int stockBefore = stockOf(skuId);

        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(2);
        dto.setAddressId(addressId);
        dto.setSkuId(skuId);
        // 立即购买链路的可购买性校验只检查上架状态，不查库存，必然走到扣减这一步
        dto.setQuantity(5);

        assertThatThrownBy(() -> orderService.submit(dto, TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.STOCK_NOT_ENOUGH.getCode());

        assertThat(stockOf(skuId)).isEqualTo(stockBefore);
        assertThat(countOrders(TEST_USER_ID)).isZero();
    }

    @Test
    @DisplayName("用他人地址下单被拒绝")
    void submit_otherUsersAddress_rejected() {
        Long otherAddressId = createAddress(OTHER_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);

        assertThatThrownBy(() -> orderService.submit(submitFromCart(otherAddressId), TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ADDRESS_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("未勾选任何商品时下单被拒绝")
    void submit_emptySelection_rejected() {
        Long addressId = createAddress(TEST_USER_ID);

        assertThatThrownBy(() -> orderService.submit(submitFromCart(addressId), TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_EMPTY.getCode());
    }

    // ==================== 取消与状态机 ====================

    @Test
    @DisplayName("取消订单精确回滚库存与销量")
    void cancel_restoresStockAndSalesExactly() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 3);
        int stockBefore = stockOf(SEED_SKU_ID);
        int salesBefore = salesOf(SEED_SKU_ID);

        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);
        assertThat(stockOf(SEED_SKU_ID)).isEqualTo(stockBefore - 3);

        orderService.cancel(order.getId(), TEST_USER_ID);

        assertThat(stockOf(SEED_SKU_ID)).isEqualTo(stockBefore);
        assertThat(salesOf(SEED_SKU_ID)).isEqualTo(salesBefore);
        OrderDetailVO cancelled = orderService.detail(order.getId(), TEST_USER_ID);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatusEnum.CANCELLED.getCode());
        assertThat(cancelled.getCancelTime()).isNotNull();
        assertThat(cancelled.getCanCancel()).isFalse();
    }

    @Test
    @DisplayName("重复取消订单：第二次被状态机拒绝，且库存不会被重复加回")
    void cancel_twice_doesNotRestoreStockTwice() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 2);
        int stockBefore = stockOf(SEED_SKU_ID);

        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);
        orderService.cancel(order.getId(), TEST_USER_ID);

        assertThatThrownBy(() -> orderService.cancel(order.getId(), TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_STATUS_INVALID.getCode());

        // 若条件更新缺失，第二次取消会把库存再加一遍 —— 凭空多出的库存最终变成超卖
        assertThat(stockOf(SEED_SKU_ID)).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("状态机：待支付不可发货、不可确认收货；已发货才可确认收货")
    void stateMachine_rejectsIllegalTransitions() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);

        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);

        assertThatThrownBy(() -> orderService.deliver(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_STATUS_INVALID.getCode());
        assertThatThrownBy(() -> orderService.finish(order.getId(), TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_STATUS_INVALID.getCode());

        // 支付环节属于阶段 5，这里直接把状态置为已支付以验证后续流转
        markPaid(order.getId());
        orderService.deliver(order.getId());
        assertThat(orderService.detail(order.getId(), TEST_USER_ID).getStatus())
                .isEqualTo(OrderStatusEnum.DELIVERED.getCode());

        // 同一状态重复发货同样被拒绝
        assertThatThrownBy(() -> orderService.deliver(order.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_STATUS_INVALID.getCode());

        orderService.finish(order.getId(), TEST_USER_ID);
        OrderDetailVO finished = orderService.detail(order.getId(), TEST_USER_ID);
        assertThat(finished.getStatus()).isEqualTo(OrderStatusEnum.FINISHED.getCode());
        assertThat(finished.getFinishTime()).isNotNull();
        assertThat(finished.getCanFinish()).isFalse();
    }

    @Test
    @DisplayName("订单是用户私有数据：他人不能查询、取消或确认收货")
    void order_otherUser_isInvisible() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);
        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);

        assertThatThrownBy(() -> orderService.detail(order.getId(), OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_NOT_FOUND.getCode());
        assertThatThrownBy(() -> orderService.cancel(order.getId(), OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_NOT_FOUND.getCode());
        assertThatThrownBy(() -> orderService.finish(order.getId(), OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.ORDER_NOT_FOUND.getCode());

        // 越权请求全部失败后，订单必须完好无损
        assertThat(orderService.detail(order.getId(), TEST_USER_ID).getStatus())
                .isEqualTo(OrderStatusEnum.WAIT_PAY.getCode());
    }

    @Test
    @DisplayName("订单明细是下单时的快照：商品改名后历史订单内容不变")
    void orderItem_isSnapshot_notLiveReference() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 1);
        OrderDetailVO order = orderService.submit(submitFromCart(addressId), TEST_USER_ID);

        String snapshotName = order.getItems().get(0).getSpuName();
        BigDecimal snapshotPrice = order.getItems().get(0).getPrice();

        // 事后改价、改名
        ProductSku update = new ProductSku();
        update.setId(SEED_SKU_ID);
        update.setPrice(new BigDecimal("1.00"));
        update.setSkuName("改名后的规格");
        skuMapper.updateById(update);

        OrderDetailVO reloaded = orderService.detail(order.getId(), TEST_USER_ID);
        assertThat(reloaded.getItems().get(0).getPrice()).isEqualByComparingTo(snapshotPrice);
        assertThat(reloaded.getItems().get(0).getSpuName()).isEqualTo(snapshotName);
    }

    @Test
    @DisplayName("下单预览只读：算出运费与应付金额，不产生订单、不扣库存")
    void preview_isReadOnly() {
        Long addressId = createAddress(TEST_USER_ID);
        addToCart(TEST_USER_ID, SEED_SKU_ID, 2);
        int stockBefore = stockOf(SEED_SKU_ID);

        OrderPreviewVO preview = orderService.preview(addressId, TEST_USER_ID);

        assertThat(preview.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9998.00"));
        assertThat(preview.getPayAmount()).isEqualByComparingTo(new BigDecimal("9998.00"));
        assertThat(preview.getItems()).hasSize(1);
        assertThat(preview.getTotalQuantity()).isEqualTo(2);
        assertThat(preview.getReceiverName()).isNotBlank();
        // 预览是只读的：用户可以反复刷新结算页
        assertThat(stockOf(SEED_SKU_ID)).isEqualTo(stockBefore);
        assertThat(countOrders(TEST_USER_ID)).isZero();
    }

    @Test
    @DisplayName("订单号唯一：连续下单不重复")
    void submit_generatesUniqueOrderNo() {
        Long addressId = createAddress(TEST_USER_ID);
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(2);
        dto.setAddressId(addressId);
        dto.setSkuId(SEED_SKU_ID);
        dto.setQuantity(1);

        String first = orderService.submit(dto, TEST_USER_ID).getOrderNo();
        String second = orderService.submit(dto, TEST_USER_ID).getOrderNo();

        assertThat(first).hasSize(24).isNotEqualTo(second);
        assertThat(countOrders(TEST_USER_ID)).isEqualTo(2);
    }

    // ==================== 测试辅助 ====================

    private Integer addToCart(Long userId, Long skuId, int quantity) {
        CartItemAddDTO dto = new CartItemAddDTO();
        dto.setSkuId(skuId);
        dto.setQuantity(quantity);
        return cartService.add(dto, userId);
    }

    private CartSelectDTO selectDto(List<Long> itemIds, boolean selected) {
        CartSelectDTO dto = new CartSelectDTO();
        dto.setItemIds(itemIds);
        dto.setSelected(selected);
        return dto;
    }

    private OrderSubmitDTO submitFromCart(Long addressId) {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setSource(1);
        dto.setAddressId(addressId);
        return dto;
    }

    private CartItem findCartItem(Long userId, Long skuId) {
        return cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));
    }

    private long countCartItems(Long userId) {
        return cartItemMapper.selectCount(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
    }

    private long countOrders(Long userId) {
        return orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId));
    }

    private int stockOf(Long skuId) {
        return skuMapper.selectById(skuId).getStock();
    }

    private int salesOf(Long skuId) {
        return skuMapper.selectById(skuId).getSales();
    }

    private void markPaid(Long orderId) {
        OrderInfo update = new OrderInfo();
        update.setId(orderId);
        update.setStatus(OrderStatusEnum.PAID.getCode());
        orderInfoMapper.updateById(update);
    }

    private Long createAddress(Long userId) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName("集成测试收货人");
        address.setReceiverPhone("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetailAddress("科技园路 1 号");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    /**
     * 造一个便宜的测试商品，用于验证运费门槛这类需要「小额订单」的场景
     * —— 种子 SKU 单价都在 4999 元以上，永远跨过了包邮门槛。
     */
    private Long createCheapSku(BigDecimal price, int stock) {
        SkuSaveDTO sku = new SkuSaveDTO();
        sku.setSkuName("集成测试规格");
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);

        SpuSaveDTO spu = new SpuSaveDTO();
        spu.setCategoryId(11L);
        spu.setName("集成测试商品");
        spu.setStatus(1);
        spu.setSkuList(List.of(sku));

        Long spuId = spuService.create(spu);
        return skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)).getId();
    }
}

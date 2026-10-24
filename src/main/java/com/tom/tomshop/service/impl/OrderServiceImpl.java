package com.tom.tomshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.constant.OrderConstants;
import com.tom.tomshop.common.enums.OrderSourceEnum;
import com.tom.tomshop.common.enums.OrderStatusEnum;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.ResultCode;
import com.tom.tomshop.convert.OrderConverter;
import com.tom.tomshop.dto.OrderQueryDTO;
import com.tom.tomshop.dto.OrderSubmitDTO;
import com.tom.tomshop.entity.CartItem;
import com.tom.tomshop.entity.OrderInfo;
import com.tom.tomshop.entity.OrderItem;
import com.tom.tomshop.entity.ProductSku;
import com.tom.tomshop.entity.ProductSpu;
import com.tom.tomshop.entity.UserAddress;
import com.tom.tomshop.exception.BusinessException;
import com.tom.tomshop.mapper.OrderInfoMapper;
import com.tom.tomshop.mapper.OrderItemMapper;
import com.tom.tomshop.mapper.ProductSkuMapper;
import com.tom.tomshop.mapper.UserAddressMapper;
import com.tom.tomshop.service.CartService;
import com.tom.tomshop.service.OrderService;
import com.tom.tomshop.service.SpuService;
import com.tom.tomshop.utils.OrderNoGenerator;
import com.tom.tomshop.utils.PageUtils;
import com.tom.tomshop.vo.CartItemVO;
import com.tom.tomshop.vo.CartVO;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.OrderItemVO;
import com.tom.tomshop.vo.OrderPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单服务实现。
 *
 * @author Tom
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper skuMapper;
    private final UserAddressMapper addressMapper;
    private final CartService cartService;
    private final SpuService spuService;
    private final OrderConverter orderConverter;

    /**
     * 下单时的一条待购明细。
     *
     * <p>它把「购物车来源」与「立即购买来源」归一成同一种形状，
     * 让后续的扣库存、算金额、写明细只有一份代码。用 record 是因为它是
     * 方法内流转的临时结构，不需要可变性。
     */
    private record OrderLine(ProductSku sku, ProductSpu spu, int quantity) {
    }

    // ==================== 下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO submit(OrderSubmitDTO dto, Long userId) {
        OrderSourceEnum source = dto.resolveSource();
        if (source == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "下单来源非法：source=" + dto.getSource());
        }

        UserAddress address = requireAddress(dto.getAddressId(), userId);
        List<OrderLine> lines = source == OrderSourceEnum.CART
                ? loadLinesFromCart(userId)
                : loadLineFromDirectBuy(dto);

        // 先扣库存再落订单：扣减失败会让事务整体回滚，此时还没有任何订单数据，
        // 回滚成本最低。反之若先落订单再扣库存，回滚要连带清理订单与明细。
        deductStocks(lines);

        BigDecimal totalAmount = calcTotalAmount(lines);
        BigDecimal freightAmount = calcFreight(totalAmount);
        // 优惠金额当前恒为 0；阶段 5 引入优惠券后由券计算填充，
        // 保留字段与计算式是为了届时不必改动 payAmount 的口径
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal payAmount = totalAmount.subtract(discountAmount).add(freightAmount);

        OrderInfo order = buildOrder(userId, dto, address, totalAmount, freightAmount,
                discountAmount, payAmount);
        orderInfoMapper.insert(order);

        List<OrderItem> items = buildOrderItems(order, lines);
        items.forEach(orderItemMapper::insert);

        // 购物车来源才清理。立即购买不该动用户的购物车 ——
        // 他可能只是顺手买一件，购物车里那些还没决定的东西不该被清掉。
        if (source == OrderSourceEnum.CART) {
            cartService.clearSelected(userId);
        }

        log.info("[下单成功] orderNo={}, userId={}, 来源={}, 明细={} 条, 应付={}",
                order.getOrderNo(), userId, source.getDesc(), items.size(), payAmount);
        return assembleDetail(order, items);
    }

    @Override
    public OrderPreviewVO preview(Long addressId, Long userId) {
        UserAddress address = requireAddress(addressId, userId);

        // 复用购物车视图：它能一次给出商品名、规格、单价、小计，并已标记失效条目。
        CartVO cart = cartService.detail(userId);
        List<CartItemVO> eligible = cart.getItems().stream()
                .filter(item -> CommonConstants.YES == (item.getSelected() == null ? 0 : item.getSelected()))
                .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
                .toList();
        if (eligible.isEmpty()) {
            throw new BusinessException(ResultCode.CART_EMPTY, "没有可结算的商品，请先勾选商品");
        }

        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setItems(eligible.stream().map(this::toPreviewItem).toList());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setReceiverAddress(joinAddress(address));

        BigDecimal totalAmount = eligible.stream()
                .map(CartItemVO::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal freightAmount = calcFreight(totalAmount);

        vo.setTotalAmount(totalAmount);
        vo.setFreightAmount(freightAmount);
        vo.setDiscountAmount(BigDecimal.ZERO);
        vo.setPayAmount(totalAmount.add(freightAmount));
        vo.setTotalQuantity(eligible.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum());
        return vo;
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<OrderDetailVO> page(OrderQueryDTO query, Long userId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                // 用户维度过滤是订单查询的第一前提，写在最前面提醒不可省略
                .eq(OrderInfo::getUserId, userId)
                .eq(query.getStatus() != null, OrderInfo::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getOrderNo()), OrderInfo::getOrderNo, query.getOrderNo());

        // 排序白名单：未命中（含未传）时退回默认排序 —— 下单时间倒序。
        // 默认排序多带一个 id 兜底：同一秒下的多笔订单若排序不稳定，
        // 翻页时会出现同一笔订单重复出现或整行被跳过
        String orderColumn = query.resolveSortField("createTime", "payAmount");
        switch (orderColumn == null ? "" : orderColumn) {
            case "createTime" -> wrapper.orderBy(true, query.isAscending(), OrderInfo::getCreateTime);
            case "payAmount" -> wrapper.orderBy(true, query.isAscending(), OrderInfo::getPayAmount);
            default -> wrapper.orderByDesc(OrderInfo::getCreateTime).orderByDesc(OrderInfo::getId);
        }

        IPage<OrderInfo> page = orderInfoMapper.selectPage(
                new Page<>(query.getPageNumValue(), query.getPageSizeValue()), wrapper);

        List<OrderDetailVO> records = orderConverter.toVOList(page.getRecords());
        fillItems(records);
        records.forEach(this::fillComputedFields);
        return PageUtils.toPageResult(page, records);
    }

    @Override
    public OrderDetailVO detail(Long orderId, Long userId) {
        // 走自定义 ResultMap（OrderInfoMapper.xml）：一次 JOIN 带出订单与全部明细
        OrderDetailVO vo = orderInfoMapper.selectOrderDetail(orderId);
        if (vo == null || !Objects.equals(vo.getUserId(), userId)) {
            // 不属于当前用户时同样报「不存在」，不暴露「订单真实存在」这一信息
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在：id=" + orderId);
        }
        fillComputedFields(vo);
        return vo;
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, Long userId) {
        OrderInfo order = requireOwnedOrder(orderId, userId);
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        if (status == null || !status.canCancel()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单当前状态不可取消：" + descOf(order.getStatus()));
        }

        // 条件更新是幂等的关键：两个并发取消请求只有一个能把状态从 10/20 改成 50，
        // 另一个受影响 0 行。库存回滚紧跟其后，因此也只会执行一次 ——
        // 若不做这个条件，同一笔订单的库存会被回滚两次，凭空多出的库存最终导致超卖。
        LambdaUpdateWrapper<OrderInfo> wrapper = new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getUserId, userId)
                .in(OrderInfo::getStatus, OrderStatusEnum.WAIT_PAY.getCode(), OrderStatusEnum.PAID.getCode())
                .set(OrderInfo::getStatus, OrderStatusEnum.CANCELLED.getCode())
                .set(OrderInfo::getCancelTime, LocalDateTime.now());
        int affected = orderInfoMapper.update(null, wrapper);
        if (affected == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单状态已变更，请刷新后重试：" + order.getOrderNo());
        }

        List<OrderItem> items = listItemsByOrderId(orderId);
        for (OrderItem item : items) {
            skuMapper.restoreStock(item.getSkuId(), item.getQuantity());
        }
        log.info("[订单取消] orderNo={}, userId={}, 回滚库存明细={} 条",
                order.getOrderNo(), userId, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long orderId) {
        OrderInfo order = requireOrder(orderId);
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        if (status == null || !status.canDeliver()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单当前状态不可发货：" + descOf(order.getStatus()));
        }

        LambdaUpdateWrapper<OrderInfo> wrapper = new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getStatus, OrderStatusEnum.PAID.getCode())
                .set(OrderInfo::getStatus, OrderStatusEnum.DELIVERED.getCode())
                .set(OrderInfo::getDeliveryTime, LocalDateTime.now());
        if (orderInfoMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单状态已变更，请刷新后重试：" + order.getOrderNo());
        }
        log.info("[订单发货] orderNo={}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long orderId, Long userId) {
        OrderInfo order = requireOwnedOrder(orderId, userId);
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        if (status == null || !status.canFinish()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单当前状态不可确认收货：" + descOf(order.getStatus()));
        }

        LambdaUpdateWrapper<OrderInfo> wrapper = new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getUserId, userId)
                .eq(OrderInfo::getStatus, OrderStatusEnum.DELIVERED.getCode())
                .set(OrderInfo::getStatus, OrderStatusEnum.FINISHED.getCode())
                .set(OrderInfo::getFinishTime, LocalDateTime.now());
        if (orderInfoMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单状态已变更，请刷新后重试：" + order.getOrderNo());
        }
        log.info("[订单完成] orderNo={}, userId={}", order.getOrderNo(), userId);
    }

    // ==================== 下单内部步骤 ====================

    /**
     * 从购物车加载待购明细。
     *
     * <p>{@code listSelectedForCheckout} 内部已做严格校验（三层上架 + 库存充足），
     * 任一商品不合格即抛异常，不会返回一份「悄悄少了几件」的清单。
     */
    private List<OrderLine> loadLinesFromCart(Long userId) {
        List<CartItem> cartItems = cartService.listSelectedForCheckout(userId);
        List<OrderLine> lines = new ArrayList<>(cartItems.size());
        for (CartItem item : cartItems) {
            SpuService.PurchasableSku purchasable = spuService.requirePurchasableSku(item.getSkuId());
            lines.add(new OrderLine(purchasable.sku(), purchasable.spu(), item.getQuantity()));
        }
        return lines;
    }

    /**
     * 立即购买：商品清单直接来自入参。
     *
     * <p>{@code skuId} / {@code quantity} 的「按 source 决定是否必填」无法用注解表达，
     * 在这里显式校验并给出可读的错误文案。
     */
    private List<OrderLine> loadLineFromDirectBuy(OrderSubmitDTO dto) {
        if (dto.getSkuId() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "立即购买必须指定商品规格（skuId）");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 1) {
            throw new BusinessException(ResultCode.CART_QUANTITY_INVALID, "购买数量至少为 1");
        }
        SpuService.PurchasableSku purchasable = spuService.requirePurchasableSku(dto.getSkuId());
        return List.of(new OrderLine(purchasable.sku(), purchasable.spu(), dto.getQuantity()));
    }

    /**
     * 逐条原子扣减库存。
     *
     * <p><b>扣减策略：条件原子 UPDATE（CAS），而非 {@code @Version} 乐观锁 + 重试。</b>
     * {@code UPDATE product_sku SET stock = stock - ? WHERE id = ? AND stock >= ?}
     * 把「判断库存够不够」与「扣减」压在同一条 SQL 的原子执行里，
     * 由 InnoDB 的行锁保证串行化。相比「查出来 → 判断 → 带 version 更新 → 失败重试」：
     * <ul>
     *   <li>只有一次数据库往返，不依赖重试；</li>
     *   <li>不存在「重试耗尽后放弃」的分支，也就不会有「库存其实够但下单失败」的误判；</li>
     *   <li>超卖由数据库层面杜绝，而不是靠应用层判断正确。</li>
     * </ul>
     * 并发下多个请求会短暂排队等行锁，这是为了正确性付出的合理代价 ——
     * 库存扣减是有界的单行写入，锁等待时间在微秒级。
     *
     * <p>注意扣减顺序：按 SKU ID 升序执行。多商品订单同时下单时，
     * 若两个事务以相反顺序锁同一批行会互相等待形成死锁；
     * 统一顺序后，后来的事务只需等待、不会成环。
     */
    private void deductStocks(List<OrderLine> lines) {
        List<OrderLine> ordered = lines.stream()
                .sorted((a, b) -> Long.compare(a.sku().getId(), b.sku().getId()))
                .toList();

        for (OrderLine line : ordered) {
            ProductSku sku = line.sku();
            int affected = skuMapper.deductStock(sku.getId(), line.quantity());
            if (affected == 0) {
                // 走到这里意味着「刚才校验时还够，扣减时不够」—— 并发下单抢走了库存。
                // 抛异常让整个事务回滚，前面已扣的 SKU 会一并还原。
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "库存不足：" + line.spu().getName() + " / " + sku.getSkuName()
                                + "，请调整数量后重试");
            }
        }
    }

    private BigDecimal calcTotalAmount(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> line.sku().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 运费：达到门槛包邮，否则收固定运费。
     *
     * <p>用 {@code compareTo} 而非 {@code equals} 比较 —— {@code BigDecimal.equals}
     * 同时比较标度，{@code 99.0} 与 {@code 99.00} 会被判为不相等，
     * 于是「刚好 99 元」的订单莫名多收 12 元运费。
     */
    private BigDecimal calcFreight(BigDecimal totalAmount) {
        return totalAmount.compareTo(OrderConstants.FREE_FREIGHT_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : OrderConstants.FLAT_FREIGHT;
    }

    private OrderInfo buildOrder(Long userId, OrderSubmitDTO dto, UserAddress address,
                                 BigDecimal totalAmount, BigDecimal freightAmount,
                                 BigDecimal discountAmount, BigDecimal payAmount) {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(generateUniqueOrderNo(userId));
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setFreightAmount(freightAmount);
        order.setPayAmount(payAmount);
        order.setPayType(dto.getPayType());
        order.setStatus(OrderStatusEnum.WAIT_PAY.getCode());
        // 收货信息写快照：用户之后修改或删除地址，历史订单的收货信息不能跟着变
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(joinAddress(address));
        order.setRemark(dto.getRemark());
        return order;
    }

    private List<OrderItem> buildOrderItems(OrderInfo order, List<OrderLine> lines) {
        List<OrderItem> items = new ArrayList<>(lines.size());
        for (OrderLine line : lines) {
            ProductSku sku = line.sku();
            ProductSpu spu = line.spu();

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            // orderNo 冗余存一份：明细表可按订单号独立查询，不必回表 order_info
            item.setOrderNo(order.getOrderNo());
            item.setSpuId(spu.getId());
            item.setSkuId(sku.getId());
            // 以下全是下单时刻的快照。商品之后改名、调价、下架都不影响历史订单，
            // 否则用户三年前订单里的商品名会变成今天的名字，对不上账
            item.setSpuName(spu.getName());
            item.setSkuName(sku.getSkuName());
            item.setSkuSpec(sku.getSpec());
            item.setProductImage(sku.getImage() != null ? sku.getImage() : spu.getMainImage());
            item.setPrice(sku.getPrice());
            item.setQuantity(line.quantity());
            item.setTotalAmount(sku.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
            items.add(item);
        }
        return items;
    }

    /**
     * 生成唯一订单号：生成 → 查重 → 命中则重试。
     *
     * <p>订单号含时间戳与 6 位随机数，碰撞概率极低但非零。查询是「先查后插」，
     * 严格来说并发下仍可能两个请求同时查到「不存在」—— 此时由
     * {@code uk_order_no} 唯一索引兜底，最终不会出现重复订单号。
     * 应用层重试的价值在于：把「极小概率的碰撞」变成自动重试，
     * 而不是让用户看到一个 5000 系统异常。
     */
    private String generateUniqueOrderNo(Long userId) {
        for (int i = 0; i < OrderConstants.ORDER_NO_MAX_RETRY; i++) {
            String orderNo = OrderNoGenerator.generate(userId);
            boolean exists = orderInfoMapper.exists(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getOrderNo, orderNo));
            if (!exists) {
                return orderNo;
            }
            log.warn("[订单号碰撞] orderNo={}, 第 {} 次重试", orderNo, i + 1);
        }
        throw new BusinessException(ResultCode.ORDER_NO_GENERATE_FAILED,
                "订单号生成失败，请稍后重试");
    }

    // ==================== 查询内部方法 ====================

    /**
     * 批量补齐明细：一次 IN 查询取回本页所有订单的明细，再按 orderId 分组。
     *
     * <p>不做「循环里查明细」—— 那是典型的 N+1，一页 10 笔订单就是 11 次 SQL。
     */
    private void fillItems(List<OrderDetailVO> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(OrderDetailVO::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        for (OrderDetailVO order : orders) {
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
            List<OrderItemVO> itemVOs = orderConverter.toItemVOList(items);
            order.setItems(itemVOs);
            order.setTotalQuantity(items.stream()
                    .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                    .sum());
        }
    }

    /**
     * 补齐由状态机派生的字段：状态文案与三个操作可用性标识。
     *
     * <p>详情接口走的是自定义 ResultMap，明细已由 SQL 带出，这里只补总数与状态相关字段。
     */
    private void fillComputedFields(OrderDetailVO vo) {
        if (vo.getItems() == null) {
            vo.setItems(new ArrayList<>());
        }
        if (vo.getTotalQuantity() == null) {
            vo.setTotalQuantity(vo.getItems().stream()
                    .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                    .sum());
        }
        if (vo.getDiscountAmount() == null) {
            vo.setDiscountAmount(BigDecimal.ZERO);
        }
        if (vo.getFreightAmount() == null) {
            vo.setFreightAmount(BigDecimal.ZERO);
        }

        OrderStatusEnum status = OrderStatusEnum.of(vo.getStatus());
        vo.setStatusDesc(descOf(vo.getStatus()));
        // 状态无法识别时（理论上不会发生，除非数据库被手工改过），
        // 三个标识一律置 false —— 拒绝操作比误放操作安全
        vo.setCanCancel(status != null && status.canCancel());
        vo.setCanDeliver(status != null && status.canDeliver());
        vo.setCanFinish(status != null && status.canFinish());
    }

    private OrderItemVO toPreviewItem(CartItemVO cartItem) {
        OrderItemVO vo = new OrderItemVO();
        vo.setSpuId(cartItem.getSpuId());
        vo.setSkuId(cartItem.getSkuId());
        vo.setSpuName(cartItem.getSpuName());
        vo.setSkuName(cartItem.getSkuName());
        vo.setSkuSpec(cartItem.getSpec());
        vo.setProductImage(cartItem.getImage());
        vo.setPrice(cartItem.getPrice());
        vo.setQuantity(cartItem.getQuantity());
        vo.setTotalAmount(cartItem.getSubtotal());
        return vo;
    }

    private OrderDetailVO assembleDetail(OrderInfo order, List<OrderItem> items) {
        OrderDetailVO vo = orderConverter.toVO(order);
        vo.setItems(orderConverter.toItemVOList(items));
        fillComputedFields(vo);
        return vo;
    }

    private List<OrderItem> listItemsByOrderId(Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    private String joinAddress(UserAddress address) {
        return String.join(" ",
                nullToEmpty(address.getProvince()),
                nullToEmpty(address.getCity()),
                nullToEmpty(address.getDistrict()),
                nullToEmpty(address.getDetailAddress())).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String descOf(Integer status) {
        OrderStatusEnum statusEnum = OrderStatusEnum.of(status);
        return statusEnum == null ? "未知状态" : statusEnum.getDesc();
    }

    // ==================== 加载与校验 ====================

    private UserAddress requireAddress(Long addressId, Long userId) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.ADDRESS_NOT_FOUND,
                    "收货地址不存在：id=" + addressId);
        }
        return address;
    }

    private OrderInfo requireOrder(Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在：id=" + orderId);
        }
        return order;
    }

    private OrderInfo requireOwnedOrder(Long orderId, Long userId) {
        OrderInfo order = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderId)
                .eq(OrderInfo::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在：id=" + orderId);
        }
        return order;
    }
}

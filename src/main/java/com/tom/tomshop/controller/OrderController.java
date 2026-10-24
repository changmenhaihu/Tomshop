package com.tom.tomshop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.context.UserContext;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.dto.OrderQueryDTO;
import com.tom.tomshop.dto.OrderSubmitDTO;
import com.tom.tomshop.service.OrderService;
import com.tom.tomshop.vo.OrderDetailVO;
import com.tom.tomshop.vo.OrderPreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>状态流转端点按「谁能触发」分组：
 * <ul>
 *   <li>买家：提交、取消、确认收货 —— 都强制带 {@code user_id} 条件，只能操作自己的订单；</li>
 *   <li>管理端：发货 —— 不限定 user_id，但需要后台权限。</li>
 * </ul>
 * 发货接口<b>阶段 3 起已受控</b>：需要登录且持有 {@code order:info:deliver} 权限点。
 * 买家侧接口（提交、取消、确认收货）除登录外不额外要求权限点 —— 它们已经通过
 * {@code user_id} 条件限定了数据范围，普通消费者本就该能用。
 *
 * @author Tom
 */
@Tag(name = "04. 订单", description = "下单、查询、取消、发货、确认收货")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交订单",
            description = "source=1 从购物车已勾选条目下单（成功后清理这些条目）；source=2 立即购买（需传 skuId 与 quantity）")
    @PostMapping
    public Result<OrderDetailVO> submit(@Valid @RequestBody OrderSubmitDTO dto) {
        return Result.success(orderService.submit(dto, UserContext.requireUserId()));
    }

    @Operation(summary = "下单前预览",
            description = "计算商品总额、运费与应付金额，供结算页展示。只读，不锁定库存、不生成订单")
    @GetMapping("/preview")
    public Result<OrderPreviewVO> preview(@Parameter(description = "收货地址 ID", required = true)
                                          @RequestParam("addressId") @NotNull(message = "请选择收货地址")
                                          Long addressId) {
        return Result.success(orderService.preview(addressId, UserContext.requireUserId()));
    }

    @Operation(summary = "我的订单分页", description = "可按状态与订单号过滤；排序白名单：createTime/payAmount")
    @GetMapping
    public Result<PageResult<OrderDetailVO>> page(@Valid OrderQueryDTO query) {
        return Result.success(orderService.page(query, UserContext.requireUserId()));
    }

    @Operation(summary = "订单详情", description = "含明细快照与状态机派生的 canCancel/canDeliver/canFinish")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id, UserContext.requireUserId()));
    }

    @Operation(summary = "取消订单",
            description = "仅待支付与已支付可取消；取消时回滚库存。重复调用第二次会因状态不匹配而失败，库存不会被重复加回")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id, UserContext.requireUserId());
        return Result.success();
    }

    @Operation(summary = "确认收货", description = "仅已发货可确认，确认后订单进入终态")
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        orderService.finish(id, UserContext.requireUserId());
        return Result.success();
    }

    @Operation(summary = "订单发货（管理端）",
            description = "仅已支付可发货。需 order:info:deliver 权限点；普通买家调用会返回 403")
    @SaCheckPermission("order:info:deliver")
    @PutMapping("/{id}/deliver")
    public Result<Void> deliver(@PathVariable Long id) {
        orderService.deliver(id);
        return Result.success();
    }
}

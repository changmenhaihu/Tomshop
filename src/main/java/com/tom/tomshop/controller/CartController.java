package com.tom.tomshop.controller;

import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.context.UserContext;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.dto.CartItemAddDTO;
import com.tom.tomshop.dto.CartItemQuantityDTO;
import com.tom.tomshop.dto.CartSelectDTO;
import com.tom.tomshop.service.CartService;
import com.tom.tomshop.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口。
 *
 * <p>当前用户身份来自 {@link UserContext#requireUserId()}。阶段 2 由 {@code X-User-Id}
 * 请求头临时注入；阶段 3 起由 {@code UserContextInterceptor} 从 Sa-Token 登录态写入，
 * 身份来源的差异被收敛在拦截器内部，本控制器与 Service 层无需改动。
 *
 * <p>购物车所有接口都要求登录 —— {@code SaTokenConfig} 的白名单不含 {@code /cart}，
 * 未携带有效令牌的请求在拦截器阶段就被拒绝。
 *
 * @author Tom
 */
@Tag(name = "03. 购物车", description = "加购、改量、勾选、删除")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "查看购物车",
            description = "含实时价格与库存；失效商品不会被过滤掉，而是以 available=false + unavailableReason 标记")
    @GetMapping
    public Result<CartVO> detail() {
        return Result.success(cartService.detail(UserContext.requireUserId()));
    }

    @Operation(summary = "加入购物车",
            description = "同一 SKU 重复加购累加数量，单 SKU 上限 999。返回该 SKU 加入后的实际数量")
    @PostMapping
    public Result<Integer> add(@Valid @RequestBody CartItemAddDTO dto) {
        return Result.success(cartService.add(dto, UserContext.requireUserId()));
    }

    @Operation(summary = "修改条目数量", description = "绝对值设值，非增量")
    @PutMapping("/items/{itemId}")
    public Result<Void> updateQuantity(@PathVariable Long itemId,
                                       @Valid @RequestBody CartItemQuantityDTO dto) {
        cartService.updateQuantity(itemId, dto.getQuantity(), UserContext.requireUserId());
        return Result.success();
    }

    @Operation(summary = "删除条目", description = "支持批量；条目 ID 不属于当前用户时按「不存在」处理")
    @DeleteMapping("/items")
    public Result<Void> remove(@Parameter(description = "条目 ID 列表")
                               @RequestParam("itemIds") @NotEmpty(message = "请选择要删除的条目")
                               List<Long> itemIds) {
        cartService.remove(itemIds, UserContext.requireUserId());
        return Result.success();
    }

    @Operation(summary = "勾选/取消勾选",
            description = "itemIds 传 null 或空数组表示全选或全不选")
    @PutMapping("/selected")
    public Result<Void> updateSelected(@Valid @RequestBody CartSelectDTO dto) {
        cartService.updateSelected(dto, UserContext.requireUserId());
        return Result.success();
    }
}

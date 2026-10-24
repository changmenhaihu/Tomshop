package com.tom.tomshop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.dto.SpuQueryDTO;
import com.tom.tomshop.dto.SpuSaveDTO;
import com.tom.tomshop.dto.StatusUpdateDTO;
import com.tom.tomshop.service.SpuService;
import com.tom.tomshop.vo.SpuDetailVO;
import com.tom.tomshop.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品 SPU 接口。
 *
 * <p><b>读写权限分离</b>：GET 接口在 {@code SaTokenConfig} 的白名单里，未登录也能浏览商品
 * （B2C 前台的基本要求）；同路径下的 POST/PUT/DELETE 属于后台维护，必须登录且持有
 * {@code product:spu:save} 或 {@code product:spu:status} 权限点。
 *
 * <p>权限点用注解声明而不是在拦截器里按路径硬编码：路径一改注解跟着方法走，
 * 不会出现「接口换了 URL 就悄悄失去保护」的情况。
 *
 * @author Tom
 */
@Tag(name = "02. 商品", description = "商品 SPU 与规格 SKU 的查询与维护")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/products")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    @Operation(summary = "商品分页列表",
            description = "支持分类（含子分类）、关键字、价格区间、状态过滤；排序字段白名单：price/sales/createTime")
    @GetMapping
    public Result<PageResult<SpuVO>> page(@Valid SpuQueryDTO query) {
        return Result.success(spuService.page(query));
    }

    @Operation(summary = "商品详情", description = "含所属分类名称与 SKU 列表")
    @GetMapping("/{id}")
    public Result<SpuDetailVO> detail(@PathVariable Long id) {
        return Result.success(spuService.detail(id));
    }

    @Operation(summary = "新增商品", description = "必须携带至少一个 SKU；展示价由 SKU 最低价自动计算。需 product:spu:save")
    @SaCheckPermission("product:spu:save")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SpuSaveDTO dto) {
        return Result.success(spuService.create(dto));
    }

    @Operation(summary = "更新商品",
            description = "SKU 全量覆盖：带 id 则更新、不带则新增、库中有而本次未提交则逻辑删除。需 product:spu:save")
    @SaCheckPermission("product:spu:save")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SpuSaveDTO dto) {
        spuService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "商品上下架", description = "1上架 0下架。需 product:spu:status")
    @SaCheckPermission("product:spu:status")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        spuService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    @Operation(summary = "删除商品", description = "逻辑删除，其下 SKU 一并逻辑删除。需 product:spu:save")
    @SaCheckPermission("product:spu:save")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        spuService.delete(id);
        return Result.success();
    }
}

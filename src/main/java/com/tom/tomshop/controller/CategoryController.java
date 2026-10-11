package com.tom.tomshop.controller;

import com.tom.tomshop.common.constant.CommonConstants;
import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.common.result.Result;
import com.tom.tomshop.dto.CategoryQueryDTO;
import com.tom.tomshop.dto.CategorySaveDTO;
import com.tom.tomshop.service.CategoryService;
import com.tom.tomshop.vo.CategoryVO;
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

import java.util.List;

/**
 * 商品分类接口。
 *
 * <p>阶段 1 的「基础 CRUD 垂直切片」，用于验证：实体映射、逻辑删除、公共字段自动填充、
 * 分页 + 排序白名单、树形组装。业务约束在阶段 2 商品管理完善。
 *
 * @author Tom
 */
@Tag(name = "01. 商品分类", description = "分类的基础 CRUD 与树形查询")
@Validated
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类树", description = "返回上架分类的完整树形结构")
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @Operation(summary = "分类分页列表")
    @GetMapping
    public Result<PageResult<CategoryVO>> page(@Valid CategoryQueryDTO query) {
        return Result.success(categoryService.page(query));
    }

    @Operation(summary = "分类详情")
    @GetMapping("/{id}")
    public Result<CategoryVO> detail(@PathVariable Long id) {
        return Result.success(categoryService.detail(id));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        return Result.success(categoryService.create(dto));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        categoryService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "逻辑删除，被逻辑删除的分类不会再出现在任何查询结果中")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}

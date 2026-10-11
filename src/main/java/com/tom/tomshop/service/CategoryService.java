package com.tom.tomshop.service;

import com.tom.tomshop.common.result.PageResult;
import com.tom.tomshop.dto.CategoryQueryDTO;
import com.tom.tomshop.dto.CategorySaveDTO;
import com.tom.tomshop.vo.CategoryVO;

import java.util.List;

/**
 * 商品分类服务。
 *
 * <p>阶段 1 用作「基础 CRUD + 树形查询」的垂直切片验证；完整的分类业务
 * （上架校验、层级约束、删除时子分类处理）在阶段 2 商品管理中进一步收敛。
 *
 * @author Tom
 */
public interface CategoryService {

    /**
     * 查询上架分类的完整树形结构。
     *
     * @return 根分类列表（children 递归挂载子分类）
     */
    List<CategoryVO> tree();

    /**
     * 分页查询分类。
     *
     * @param query 分页与过滤条件
     * @return 分页结果
     */
    PageResult<CategoryVO> page(CategoryQueryDTO query);

    /**
     * 分类详情。
     *
     * @param id 分类 ID
     * @return 分类视图
     */
    CategoryVO detail(Long id);

    /**
     * 新增分类。
     *
     * @param dto 分类信息
     * @return 新分类 ID
     */
    Long create(CategorySaveDTO dto);

    /**
     * 更新分类（传什么改什么，null 字段不覆盖）。
     *
     * @param id  分类 ID
     * @param dto 待更新的字段
     */
    void update(Long id, CategorySaveDTO dto);

    /**
     * 删除分类（逻辑删除）。
     *
     * @param id 分类 ID
     */
    void delete(Long id);
}

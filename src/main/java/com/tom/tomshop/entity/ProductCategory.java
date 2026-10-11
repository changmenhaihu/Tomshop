package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类实体（表 product_category），树形结构（parent_id 自关联）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_category")
public class ProductCategory extends BaseEntity {

    /** 父分类 ID，0 表示根分类 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 层级：1一级 2二级 3三级 */
    private Integer level;

    /** 同级排序，值越小越靠前 */
    private Integer sort;

    /** 分类图标 URL */
    private String icon;

    /** 状态：1上架 0下架 */
    private Integer status;
}

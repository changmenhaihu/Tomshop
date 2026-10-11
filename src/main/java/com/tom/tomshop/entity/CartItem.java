package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车条目实体（表 cart_item）。
 *
 * <p>约束：同一用户 + 同一 SKU 在表中唯一（uk_user_sku），重复加购走「数量累加」而非新增行。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_item")
public class CartItem extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 是否选中：1选中 0未选中 */
    private Integer selected;
}

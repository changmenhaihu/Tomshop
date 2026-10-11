package com.tom.tomshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tom.tomshop.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址实体（表 user_address）。
 *
 * @author Tom
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class UserAddress extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址（街道门牌） */
    private String detailAddress;

    /** 是否默认地址：1是 0否 */
    private Integer isDefault;
}

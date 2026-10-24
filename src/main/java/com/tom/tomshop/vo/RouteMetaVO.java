package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 动态路由的元信息，对应 vue-router 的 {@code meta} 字段。
 *
 * <p>字段名刻意与主流前端后台模板（vue-element-admin / RuoYi-Vue）保持一致，
 * 前端拿到后可以直接灌进 {@code router.addRoute()}，不需要再做一层字段映射。
 *
 * @author Tom
 */
@Data
@Builder
@Schema(description = "路由元信息")
public class RouteMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "菜单标题，用于侧边栏与面包屑", example = "用户管理")
    private String title;

    @Schema(description = "图标名称（前端图标库的 key）", example = "user")
    private String icon;

    @Schema(description = "是否在侧边栏隐藏。true 表示路由照常注册但不展示（如详情页）", example = "false")
    private Boolean hidden;

    @Schema(description = "同级排序，值越小越靠前", example = "1")
    private Integer sort;
}

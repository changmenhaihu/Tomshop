package com.tom.tomshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 动态路由节点，前端可直接交给 {@code router.addRoute()} 注册。
 *
 * <p><b>字段语义（与 vue-router 对齐）</b>：
 * <ul>
 *   <li>{@link #path}：绝对路径，如 {@code /system}、{@code /system/user}。
 *       子路由也用绝对路径，避免「父路径改写」带来的歧义；</li>
 *   <li>{@link #name}：路由唯一名，由路径推导出的 PascalCase（{@code /system/user → SystemUser}）。
 *       {@code keep-alive} 与 {@code router.push({name})} 都依赖它，必须全局唯一；</li>
 *   <li>{@link #component}：目录型路由固定为 {@code Layout}（前端布局组件），
 *       菜单型路由为 {@code sys_menu.component} 里配置的组件路径；</li>
 *   <li>{@link #redirect}：仅目录型路由有值，指向第一个子菜单，
 *       否则前端访问 {@code /system} 会渲染出一个空布局。</li>
 * </ul>
 *
 * @author Tom
 */
@Data
@Builder
@Schema(description = "动态路由节点")
public class RouteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "路由路径，绝对路径", example = "/system/user")
    private String path;

    @Schema(description = "路由名称，PascalCase 且全局唯一", example = "SystemUser")
    private String name;

    @Schema(description = "组件路径；目录型路由为 Layout", example = "system/user/index")
    private String component;

    @Schema(description = "重定向地址，仅目录型路由有值", example = "/system/user")
    private String redirect;

    @Schema(description = "路由元信息")
    private RouteMetaVO meta;

    @Schema(description = "子路由，菜单型节点为空数组")
    private List<RouteVO> children;
}

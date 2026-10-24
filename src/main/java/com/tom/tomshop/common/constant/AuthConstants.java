package com.tom.tomshop.common.constant;

/**
 * 认证模块常量。
 *
 * <p>只放与令牌格式强相关的字面量。可调参数（有效期、Redis 键前缀）属于配置项，
 * 收敛在 {@code tomshop.auth.*}，见 {@code TomShopProperties.Auth}，不要写死在这里。
 *
 * @author Tom
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** JWT 自定义声明：refresh token 的唯一标识，用于 Redis 侧吊销与轮换 */
    public static final String CLAIM_JTI = "jti";

    /** JWT 自定义声明：令牌类型，用以区分「访问令牌」与「刷新令牌」两种用途 */
    public static final String CLAIM_TYPE = "typ";

    /** 令牌类型取值：刷新令牌 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 账号状态：正常 */
    public static final int USER_STATUS_ENABLED = 1;

    /** 菜单状态：启用 */
    public static final int MENU_STATUS_ENABLED = 1;

    /** 菜单类型：目录（仅分组，不对应具体组件） */
    public static final int MENU_TYPE_DIRECTORY = 1;

    /** 菜单是否隐藏：显示 */
    public static final int MENU_VISIBLE_SHOW = 0;

    /** 目录型菜单在前端使用的布局组件名（vue-router 约定） */
    public static final String LAYOUT_COMPONENT = "Layout";

    /** 注册时分配的默认角色编码 */
    public static final String DEFAULT_ROLE_CODE = "USER";
}

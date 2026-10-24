package com.tom.tomshop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 认证与鉴权集成测试（阶段 3）。
 *
 * <p>与阶段 2 的测试不同，这里走的是完整的 HTTP 链路（MockMvc + 拦截器 + 注解），
 * 因此验证的是「<b>谁能访问什么</b>」而不是「方法本身对不对」：
 * <ul>
 *   <li>未登录访问受保护接口 → HTTP 401，且响应体 code 也是 401（前端拦截器依赖它触发刷新）；</li>
 *   <li>已登录但缺少权限点 → HTTP 403，且业务码 403；</li>
 *   <li>商品与分类的 GET 匿名放行，但同路径的写操作不放行 —— 这是最容易配错的一处；</li>
 *   <li>refresh token 一次性消费：重复使用旧令牌必须失败，这是发现令牌泄露的关键信号。</li>
 * </ul>
 *
 * <p>数据隔离：类级 {@code @Transactional} 让所有数据库写入在测试结束后回滚；
 * 注册出来的账号名带随机后缀，即便某次运行没能回滚也不会与真实账号冲突。
 * Redis 中的会话与 refresh token 不会回滚，但它们都有 TTL，会自行过期。
 *
 * <p>依赖前置条件：开发库里存在种子管理员 {@code admin / admin123}
 * （见 {@code db/migration/2026-10-24__seed_admin_user.sql}）。
 *
 * @author Tom
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String API = "/api/v1";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private MockMvc mockMvc;

    // ==================== 登录 ====================

    @Test
    @DisplayName("管理员登录成功，返回 access/refresh 双令牌与权限列表")
    void loginSucceedsWithAdmin() throws Exception {
        Response resp = call(post(API + "/auth/login"), Map.of(
                "username", ADMIN_USERNAME, "password", ADMIN_PASSWORD), null);

        assertThat(resp.status).isEqualTo(200);
        JsonNode data = resp.body.path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.path("expiresIn").asLong()).isPositive();

        JsonNode user = data.path("user");
        assertThat(user.path("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(user.path("password").isMissingNode()).isTrue();
        assertThat(toList(user.path("roles"))).containsExactly("ADMIN");
        assertThat(toList(user.path("permissions")))
                .contains("product:spu:save", "order:info:deliver", "system:user:query");
    }

    @Test
    @DisplayName("密码错误返回 401")
    void loginRejectsWrongPassword() throws Exception {
        Response resp = call(post(API + "/auth/login"), Map.of(
                "username", ADMIN_USERNAME, "password", "definitely-wrong"), null);

        assertThat(resp.status).isEqualTo(401);
        assertThat(resp.body.path("code").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("账号不存在与密码错误的提示一致，避免账号枚举")
    void loginDoesNotRevealWhetherAccountExists() throws Exception {
        Response wrongPassword = call(post(API + "/auth/login"), Map.of(
                "username", ADMIN_USERNAME, "password", "definitely-wrong"), null);
        Response unknownUser = call(post(API + "/auth/login"), Map.of(
                "username", "no_such_user_9x", "password", "definitely-wrong"), null);

        assertThat(unknownUser.status).isEqualTo(wrongPassword.status);
        assertThat(unknownUser.body.path("code").asInt()).isEqualTo(wrongPassword.body.path("code").asInt());
        assertThat(unknownUser.body.path("message").asText())
                .isEqualTo(wrongPassword.body.path("message").asText());
    }

    // ==================== 匿名访问边界 ====================

    @Test
    @DisplayName("匿名可浏览商品与分类（B2C 前台）")
    void anonymousCanBrowseProducts() throws Exception {
        assertThat(call(get(API + "/products"), null, null).status).isEqualTo(200);
        assertThat(call(get(API + "/categories/tree"), null, null).status).isEqualTo(200);
    }

    @Test
    @DisplayName("未登录访问购物车返回 401，且 HTTP 状态与业务码一致")
    void anonymousIsDeniedOnCart() throws Exception {
        Response resp = call(get(API + "/cart"), null, null);
        assertThat(resp.status).isEqualTo(401);
        assertThat(resp.body.path("code").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("同一路径的写操作不随 GET 一起放行")
    void anonymousCannotWriteProducts() throws Exception {
        Response resp = call(post(API + "/products"), Map.of("name", "x"), null);
        assertThat(resp.status).isEqualTo(401);
    }

    @Test
    @DisplayName("令牌缺少 Bearer 前缀时按未登录处理")
    void tokenWithoutPrefixIsRejected() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD).path("accessToken").asText();

        MvcResult result = mockMvc.perform(get(API + "/auth/me").header("Authorization", token)).andReturn();
        Response resp = Response.of(result);

        assertThat(resp.status).isEqualTo(401);
    }

    @Test
    @DisplayName("伪造令牌返回 401")
    void forgedTokenIsRejected() throws Exception {
        Response resp = call(get(API + "/auth/me"), null, "not.a.real.jwt");
        assertThat(resp.status).isEqualTo(401);
    }

    // ==================== 当前用户与动态路由 ====================

    @Test
    @DisplayName("/auth/me 返回角色与权限，且不含密码字段")
    void meReturnsRolesAndPermissions() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD).path("accessToken").asText();

        Response resp = call(get(API + "/auth/me"), null, token);

        assertThat(resp.status).isEqualTo(200);
        JsonNode data = resp.body.path("data");
        assertThat(data.path("username").asText()).isEqualTo(ADMIN_USERNAME);
        assertThat(data.path("password").isMissingNode()).isTrue();
        assertThat(toList(data.path("roles"))).containsExactly("ADMIN");
        assertThat(toList(data.path("permissions"))).hasSize(18);
    }

    @Test
    @DisplayName("管理员动态路由：目录用 Layout + redirect，路由名为 PascalCase")
    void routesForAdminFormAUsableTree() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD).path("accessToken").asText();

        Response resp = call(get(API + "/auth/routes"), null, token);
        assertThat(resp.status).isEqualTo(200);

        JsonNode roots = resp.body.path("data");
        assertThat(roots.isArray()).isTrue();
        assertThat(roots).hasSize(3);
        assertThat(roots.findValuesAsText("path"))
                .contains("/system", "/product", "/order");

        JsonNode system = findRoute(roots, "/system");
        assertThat(system.path("component").asText()).isEqualTo("Layout");
        assertThat(system.path("name").asText()).isEqualTo("System");
        // 目录必须有 redirect，否则前端访问 /system 会渲染空布局
        assertThat(system.path("redirect").asText()).isEqualTo("/system/user");
        assertThat(system.path("meta").path("title").asText()).isNotBlank();
        assertThat(system.path("meta").path("hidden").asBoolean()).isFalse();

        List<String> childPaths = new ArrayList<>();
        List<String> childNames = new ArrayList<>();
        system.path("children").forEach(child -> {
            childPaths.add(child.path("path").asText());
            childNames.add(child.path("name").asText());
            assertThat(child.path("component").asText()).isNotBlank();
        });
        assertThat(childPaths).containsExactly(
                "/system/user", "/system/role", "/system/menu", "/system/permission");
        assertThat(childNames).containsExactly(
                "SystemUser", "SystemRole", "SystemMenu", "SystemPermission");
    }

    @Test
    @DisplayName("没有菜单授权的普通用户拿到空路由与空权限")
    void ordinaryUserHasNoAdminRoutesAndPermissions() throws Exception {
        JsonNode registered = register("ut_user_" + System.nanoTime());
        String token = registered.path("accessToken").asText();

        assertThat(toList(registered.path("user").path("permissions"))).isEmpty();
        assertThat(toList(registered.path("user").path("roles"))).containsExactly("USER");

        Response resp = call(get(API + "/auth/routes"), null, token);
        assertThat(resp.status).isEqualTo(200);
        assertThat(resp.body.path("data").size()).isZero();
    }

    // ==================== 权限拦截 ====================

    @Test
    @DisplayName("普通用户可读商品但不能新增商品、不能发货")
    void ordinaryUserIsDeniedOnManagementEndpoints() throws Exception {
        String token = register("ut_user_" + System.nanoTime()).path("accessToken").asText();

        assertThat(call(get(API + "/products"), null, token).status).isEqualTo(200);

        Response create = call(post(API + "/products"), Map.of("name", "x"), token);
        assertThat(create.status).isEqualTo(403);
        assertThat(create.body.path("code").asInt()).isEqualTo(403);

        Response deliver = call(put(API + "/orders/1/deliver"), null, token);
        assertThat(deliver.status).isEqualTo(403);
    }

    // ==================== 令牌生命周期 ====================

    @Test
    @DisplayName("刷新令牌轮换：旧 refresh token 用一次即失效")
    void refreshRotatesAndInvalidatesPreviousToken() throws Exception {
        JsonNode loginData = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String oldRefresh = loginData.path("refreshToken").asText();
        String oldAccess = loginData.path("accessToken").asText();

        Response refreshed = call(post(API + "/auth/refresh"),
                Map.of("refreshToken", oldRefresh), null);
        assertThat(refreshed.status).isEqualTo(200);
        String newAccess = refreshed.body.path("data").path("accessToken").asText();
        String newRefresh = refreshed.body.path("data").path("refreshToken").asText();
        assertThat(newAccess).isNotEqualTo(oldAccess);
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // 旧 refresh token 已被消费，再次使用必须失败
        Response reuse = call(post(API + "/auth/refresh"), Map.of("refreshToken", oldRefresh), null);
        assertThat(reuse.status).isEqualTo(401);
        assertThat(reuse.body.path("code").asInt()).isEqualTo(401);

        // 新令牌可用
        assertThat(call(get(API + "/auth/me"), null, newAccess).status).isEqualTo(200);
    }

    @Test
    @DisplayName("access token 不能当作 refresh token 使用")
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        JsonNode data = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        Response resp = call(post(API + "/auth/refresh"),
                Map.of("refreshToken", data.path("accessToken").asText()), null);

        assertThat(resp.status).isEqualTo(401);
    }

    @Test
    @DisplayName("登出后 access token 与 refresh token 同时失效")
    void logoutInvalidatesBothTokens() throws Exception {
        JsonNode data = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String access = data.path("accessToken").asText();
        String refresh = data.path("refreshToken").asText();

        assertThat(call(post(API + "/auth/logout"), null, access).status).isEqualTo(200);

        assertThat(call(get(API + "/auth/me"), null, access).status).isEqualTo(401);
        assertThat(call(post(API + "/auth/refresh"), Map.of("refreshToken", refresh), null).status)
                .isEqualTo(401);
    }

    // ==================== 辅助 ====================

    private JsonNode login(String username, String password) throws Exception {
        Response resp = call(post(API + "/auth/login"),
                Map.of("username", username, "password", password), null);
        assertThat(resp.status).as("登录失败：%s", resp.body).isEqualTo(200);
        return resp.body.path("data");
    }

    private JsonNode register(String username) throws Exception {
        Response resp = call(post(API + "/auth/register"),
                Map.of("username", username, "password", "ut123456"), null);
        assertThat(resp.status).as("注册失败：%s", resp.body).isEqualTo(200);
        return resp.body.path("data");
    }

    private Response call(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(JSON.writeValueAsString(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Response.of(mockMvc.perform(builder).andReturn());
    }

    private static JsonNode findRoute(JsonNode routes, String path) {
        for (JsonNode route : routes) {
            if (path.equals(route.path("path").asText())) {
                return route;
            }
        }
        throw new AssertionError("路由不存在：" + path);
    }

    private static List<String> toList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** MockMvc 结果的最小封装：UTF-8 解码 + JSON 解析，避免每个用例重复样板代码 */
    private record Response(int status, JsonNode body) {

        static Response of(MvcResult result) throws Exception {
            String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode body = content.isBlank() ? JSON.createObjectNode() : JSON.readTree(content);
            return new Response(result.getResponse().getStatus(), body);
        }
    }
}

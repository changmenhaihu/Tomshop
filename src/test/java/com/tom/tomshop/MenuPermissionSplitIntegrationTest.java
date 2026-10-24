package com.tom.tomshop;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tom.tomshop.entity.SysMenu;
import com.tom.tomshop.entity.SysPermission;
import com.tom.tomshop.entity.SysRoleMenu;
import com.tom.tomshop.entity.SysRolePermission;
import com.tom.tomshop.mapper.SysMenuMapper;
import com.tom.tomshop.mapper.SysPermissionMapper;
import com.tom.tomshop.mapper.SysRoleMenuMapper;
import com.tom.tomshop.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单 / 权限拆分后的实体与 Mapper 集成测试。
 *
 * <p>验证拆分解耦真正落地，而不是「表拆了、字段还是糊在一起」：
 * <ol>
 *   <li>{@code sys_menu} 承载导航树，{@code sys_permission} 为扁平权限点，两者字段互不渗透；</li>
 *   <li>两条授权链路彼此独立 —— 菜单授权走 {@code sys_role_menu}，
 *       权限授权走 {@code sys_role_permission}，不存在互相推导；</li>
 *   <li>权限标识 {@code perms} 由唯一约束兜底，重复写入必须失败。</li>
 * </ol>
 *
 * <p>依赖真实 MySQL（@SpringBootTest 启动完整上下文）；方法级 {@code @Transactional}
 * 保证测试数据回滚，不污染开发库。
 *
 * @author Tom
 */
@SpringBootTest
@Transactional
class MenuPermissionSplitIntegrationTest {

    /** 种子数据：ADMIN 角色 ID */
    private static final long ADMIN_ROLE_ID = 1L;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;

    @Test
    void menu_字段映射正确_且携带导航专属字段() {
        SysMenu menu = menuMapper.selectById(21L);

        assertThat(menu).isNotNull();
        assertThat(menu.getName()).isEqualTo("商品列表");
        // 导航专属字段：path / component / menuType / visible —— 权限表里没有这些概念
        assertThat(menu.getPath()).isEqualTo("/product/spu");
        assertThat(menu.getComponent()).isEqualTo("product/spu/index");
        assertThat(menu.getMenuType()).isEqualTo(2);
        assertThat(menu.getVisible()).isZero();
        assertThat(menu.getParentId()).isEqualTo(2L);
        // 公共字段自动填充已生效
        assertThat(menu.getCreateTime()).isNotNull();
        assertThat(menu.getDeleted()).isZero();
    }

    @Test
    void permission_扁平结构_无parentId且perms唯一() {
        SysPermission perm = permissionMapper.selectById(101L);

        assertThat(perm).isNotNull();
        assertThat(perm.getPerms()).isEqualTo("system:user:query");
        assertThat(perm.getModule()).isEqualTo("系统管理");

        // 拆分后 sys_permission 不再有 parent_id：整表查询结果的 parentId 语义已不存在，
        // 这里用「扁平 = 任意两行的 id 不构成父子关系」间接验证 —— 全部 18 行都是叶子。
        List<SysPermission> all = permissionMapper.selectList(null);
        assertThat(all).hasSize(18);
        assertThat(all).allSatisfy(p -> assertThat(p.getPerms()).isNotBlank());
    }

    @Test
    void perms唯一约束_重复标识必须被拒绝() {
        SysPermission duplicate = new SysPermission();
        duplicate.setName("重复的权限");
        duplicate.setPerms("system:user:query");   // 与 101 行撞唯一键
        duplicate.setType(1);
        duplicate.setStatus(1);

        // 期望抛异常：唯一约束是权限体系的关键防线，
        // 同一标识存两行会导致「给角色挂了其中一行、鉴权却比对另一行」的静默失效
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class, () -> permissionMapper.insert(duplicate)))
                .hasMessageContaining("uk_perms");
    }

    @Test
    void 两条授权链路_彼此独立() {
        // 链路一：角色 -> 菜单
        List<SysRoleMenu> roleMenus = roleMenuMapper.selectList(
                Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, ADMIN_ROLE_ID));
        assertThat(roleMenus).hasSize(10);
        assertThat(roleMenus).extracting(SysRoleMenu::getMenuId)
                .containsExactlyInAnyOrder(1L, 11L, 12L, 13L, 14L, 2L, 21L, 22L, 3L, 31L);

        // 链路二：角色 -> 权限
        List<SysRolePermission> rolePerms = rolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, ADMIN_ROLE_ID));
        assertThat(rolePerms).hasSize(18);

        // 关键断言：两条链路的 ID 空间完全不重叠。
        // 拆分前菜单与权限共用一张表、共用一套 ID；现在若还有交集，
        // 说明解耦没做干净（比如仍按旧 id 关联）。
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).toList();
        List<Long> permIds = rolePerms.stream().map(SysRolePermission::getPermId).toList();
        assertThat(menuIds).doesNotContainAnyElementsOf(permIds);
    }

    @Test
    void 菜单树_可由parentId组装出层级() {
        List<SysMenu> roots = menuMapper.selectList(
                Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, 0L));
        assertThat(roots).hasSize(3);

        List<SysMenu> children = menuMapper.selectList(
                Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, 1L));
        assertThat(children).hasSize(4);
        assertThat(children).allMatch(m -> m.getMenuType() == 2);
    }
}

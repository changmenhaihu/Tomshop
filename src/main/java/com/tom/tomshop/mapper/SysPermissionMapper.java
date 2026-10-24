package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.SysPermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限点 Mapper。菜单相关查询见 {@link SysMenuMapper}。
 *
 * @author Tom
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询某用户最终生效的权限标识集合（去重）。
     *
     * <p>链路：{@code sys_user_role → sys_role → sys_role_permission → sys_permission}。
     * 三段 JOIN 的条件必须同时成立才算「授权生效」：
     * <ul>
     *   <li>角色未删除且 {@code status = 1}（禁用角色等于收回其全部授权）；</li>
     *   <li>权限点未删除且 {@code status = 1}（下线一个权限点时，无需逐个角色解绑）。</li>
     * </ul>
     *
     * <p>自定义 SQL 不会被 MyBatis-Plus 的逻辑删除插件改写，{@code deleted = 0} 必须手写。
     *
     * @param userId 用户 ID
     * @return 权限标识列表，如 {@code product:spu:save}；无授权时返回空集合
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}

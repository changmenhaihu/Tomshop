package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.SysMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 Mapper。
 *
 * @author Tom
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询某用户可见的菜单（扁平列表，树形结构在 Service 层组装）。
     *
     * <p>与权限点是两条彼此独立的授权链路：本方法只看 {@code sys_role_menu}，
     * 与 {@code sys_role_permission} 无关。一个用户能「看见」某个菜单，
     * 不代表能在该菜单下执行写操作。
     *
     * <p>排序在 SQL 里完成（{@code sort, id}），因为同级的展示顺序属于数据属性；
     * Service 层组装时无需再排一次，只需保证父节点先于子节点入表即可（本方法已按此保证）。
     *
     * @param userId 用户 ID
     * @return 启用且未被删除的菜单列表；无授权时返回空集合
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}

package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.SysRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper。
 *
 * @author Tom
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询某用户拥有的角色编码（去重），供 {@code StpInterface#getRoleList} 消费。
     *
     * @param userId 用户 ID
     * @return 角色编码列表，如 {@code ADMIN}；无角色时返回空集合
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}

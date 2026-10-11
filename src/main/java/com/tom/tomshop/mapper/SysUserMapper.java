package com.tom.tomshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tom.tomshop.entity.SysUser;

/**
 * 用户 Mapper。通用 CRUD 由 MyBatis-Plus {@link BaseMapper} 提供，
 * 复杂 SQL（多表关联、聚合统计）在 {@code resources/mapper} 下同名 XML 中维护。
 *
 * @author Tom
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}

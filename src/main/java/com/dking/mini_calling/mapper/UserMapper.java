package com.dking.mini_calling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dking.mini_calling.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserMapper> {
    /** 按用户名查（登录用），自动过滤已逻辑删除的 */
    SysUser selectByUsername(String username);

    /** 按 id 查 */
    SysUser selectById(Long id);
}

package com.dking.mini_calling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dking.mini_calling.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {
    /** 查某用户拥有的所有角色（经 sys_user_role 关联） */
    List<SysRole> selectByUserId(Long userId);
}

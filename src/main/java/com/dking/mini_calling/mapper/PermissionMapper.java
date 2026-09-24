package com.dking.mini_calling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dking.mini_calling.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {

    /** 查某用户的所有权限：user -> user_role -> role_permission -> permission */
    List<SysPermission> selectByUserId(Long userId);
}

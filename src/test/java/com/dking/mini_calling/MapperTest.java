package com.dking.mini_calling;

import com.dking.mini_calling.entity.SysPermission;
import com.dking.mini_calling.entity.SysRole;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.mapper.PermissionMapper;
import com.dking.mini_calling.mapper.RoleMapper;
import com.dking.mini_calling.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class MapperTest {

    @Autowired
    UserMapper userMapper;
    @Autowired
    RoleMapper roleMapper;
    @Autowired
    PermissionMapper permissionMapper;

    @Test
    void testAdmin() {
        SysUser admin = userMapper.selectByUsername("admin");
        System.out.println("用户: " + admin.getNickname());          // 超级管理员

        List<SysRole> roles = roleMapper.selectByUserId(admin.getId());
        roles.forEach(r -> System.out.println("角色: " + r.getCode())); // ROLE_ADMIN

        List<SysPermission> perms = permissionMapper.selectByUserId(admin.getId());
        System.out.println("权限数: " + perms.size());                 // 0（还没插权限数据，正常）
    }
}

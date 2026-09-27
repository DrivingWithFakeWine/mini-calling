package com.dking.mini_calling.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dking.mini_calling.BaseIntegrationTest;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RoleUpdateRequest;
import com.dking.mini_calling.entity.SysRole;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.entity.SysUserRole;
import com.dking.mini_calling.mapper.RoleMapper;
import com.dking.mini_calling.mapper.SysUserRoleMapper;
import com.dking.mini_calling.mapper.UserMapper;
import com.dking.mini_calling.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RoleServiceTest extends BaseIntegrationTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Test
    @DisplayName("新增角色成功并绑定初始权限")
    void createRole_withPermissions() {
        RoleCreateRequest req = new RoleCreateRequest();
        req.setCode("editor_v2");
        req.setName("编辑员V2");
        req.setPermissionIds(java.util.List.of(1L, 5L));  // 按你的 permissionIds 调整

        roleService.createRole(req);

        var role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "editor_v2"));
        assertThat(role).isNotNull();
        assertThat(roleMapper.selectPermissionIdsByRoleId(role.getId()))
                .containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    @DisplayName("编辑角色：先删后插，权限绑定被完全替换")
    void updateRole_replacesPermissions() {
        RoleCreateRequest create = new RoleCreateRequest();
        create.setCode("to_update");
        create.setName("待编辑");
        create.setPermissionIds(List.of(1L, 5L));
        roleService.createRole(create);
        Long roleId = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "to_update")).getId();

        RoleUpdateRequest req = new RoleUpdateRequest();
        req.setName("改名了");
        req.setPermissionIds(List.of(2L));
        roleService.updateRole(roleId, req);

        assertThat(roleMapper.selectPermissionIdsByRoleId(roleId)).containsExactly(2L);
    }

    @Test
    @DisplayName("删除角色：同事务清理两张关联表")
    void deleteRole_cleansUpAssociations() {
        // 先造数据
        RoleCreateRequest create = new RoleCreateRequest();
        create.setCode("to_delete");
        create.setName("待删除");
        create.setPermissionIds(java.util.List.of(1L));
        roleService.createRole(create);
        Long roleId = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "to_delete")).getId();
        SysUser zhangsan = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "zhangsan"));
        userMapper.batchInsertUserRoles(zhangsan.getId(), List.of(roleId));

        roleService.deleteRole(roleId);

        assertThat(roleMapper.selectById(roleId)).isNull();
        assertThat(roleMapper.selectPermissionIdsByRoleId(roleId)).isEmpty();
        assertThat(userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId)))
                .isZero();
    }

    @Test
    @DisplayName("admin 角色禁止删除")
    void deleteRole_admin_throws() {
        Long adminId = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "admin")).getId();

        assertThatThrownBy(() -> roleService.deleteRole(adminId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许删除");
    }
}


package com.dking.mini_calling.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.entity.SysUser;

import java.util.List;

// service/UserService.java
public interface UserService {
    IPage<UserPageResponse> pageUsers(long page, long size, String keyword);
    /** 创建用户，返回新用户 id（agent 工具与 Controller 共用） */
    Long createUser(UserCreateRequest request);
    void deleteUser(Long id);
    void changePassword(Long userId, ChangePasswordRequest request);
    void assignRoles(Long userId, List<Long> roleIds);
    /** 启用/禁用用户 */
    void setEnabled(Long userId, boolean enabled);
    /**
     * 按用户名查询用户实体（内部/测试用，不要直接暴露给 Controller）
     */
    SysUser getUserByUsername(String username);
    /** 按 id 查询用户实体；逻辑删除/不存在时返回 null */
    SysUser getUserById(Long id);
}


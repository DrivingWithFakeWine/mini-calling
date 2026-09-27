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
    void createUser(UserCreateRequest request);
    void deleteUser(Long id);
    void changePassword(Long userId, ChangePasswordRequest request);
    void assignRoles(Long userId, List<Long> roleIds);
    /**
     * 按用户名查询用户实体（内部/测试用，不要直接暴露给 Controller）
     */
    SysUser getUserByUsername(String username);
}


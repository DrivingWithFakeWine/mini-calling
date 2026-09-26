package com.dking.mini_calling.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;

import java.util.List;

// service/UserService.java
public interface UserService {
    IPage<UserPageResponse> pageUsers(long page, long size, String keyword);
    void createUser(UserCreateRequest request);
    void deleteUser(Long id);
    void changePassword(Long userId, ChangePasswordRequest request);
    void assignRoles(Long userId, List<Long> roleIds);
}


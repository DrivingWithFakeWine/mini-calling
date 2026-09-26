package com.dking.mini_calling.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RolePageResponse;
import com.dking.mini_calling.dto.RoleUpdateRequest;

public interface RoleService {
    IPage<RolePageResponse> pageRoles(long page, long size, String keyword);
    void createRole(RoleCreateRequest request);
    void updateRole(Long id, RoleUpdateRequest request);
    void deleteRole(Long id);
}

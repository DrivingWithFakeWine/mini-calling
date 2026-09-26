package com.dking.mini_calling.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RolePageResponse;
import com.dking.mini_calling.dto.RoleUpdateRequest;
import com.dking.mini_calling.entity.SysRole;
import com.dking.mini_calling.mapper.RoleMapper;
import com.dking.mini_calling.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    @Override
    public IPage<RolePageResponse> pageRoles(long page, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysRole::getCode, keyword)
                        .or()
                        .like(SysRole::getName, keyword))
                .orderByAsc(SysRole::getId);

        Page<SysRole> result = roleMapper.selectPage(new Page<>(page, size), wrapper);

        // 一次性查本页所有角色的权限 ID，避免 N+1
        List<Long> roleIds = result.getRecords().stream().map(SysRole::getId).toList();
        Map<Long, List<Long>> permMap = Collections.emptyMap();
        if (!roleIds.isEmpty()) {
            permMap = roleMapper.selectPermissionsByRoleIds(roleIds).stream()
                    .collect(Collectors.groupingBy(
                            r -> ((Number) r.get("role_id")).longValue(),
                            Collectors.mapping(
                                    r -> ((Number) r.get("permission_id")).longValue(),
                                    Collectors.toList())));
        }

        Map<Long, List<Long>> finalPermMap = permMap;
        return result.convert(role -> toResponse(role, finalPermMap.getOrDefault(role.getId(), List.of())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleCreateRequest req) {
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.getCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }

        SysRole role = new SysRole();
        role.setCode(req.getCode());
        role.setName(req.getName());
        roleMapper.insert(role);   // MP 回填主键

        if (req.getPermissionIds() != null && !req.getPermissionIds().isEmpty()) {
            roleMapper.batchInsertRolePermission(role.getId(), req.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleUpdateRequest req) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if ("admin".equals(role.getCode())) {
            throw new BusinessException("内置管理员角色仅允许修改名称");
        }

        role.setName(req.getName());
        roleMapper.updateById(role);

        // 关键：先删后插的幂等写法，避免撞主键或漏绑
        roleMapper.deleteRolePermissionByRoleId(id);
        if (req.getPermissionIds() != null && !req.getPermissionIds().isEmpty()) {
            roleMapper.batchInsertRolePermission(id, req.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if ("admin".equals(role.getCode())) {
            throw new BusinessException("不允许删除内置管理员角色");
        }

        // 三步必须同事务：清用户关联 → 清权限关联 → 删角色
        roleMapper.deleteUserRoleByRoleId(id);
        roleMapper.deleteRolePermissionByRoleId(id);
        roleMapper.deleteById(id);
    }

    private RolePageResponse toResponse(SysRole role, List<Long> permissionIds) {
        RolePageResponse resp = new RolePageResponse();
        resp.setId(role.getId());
        resp.setCode(role.getCode());
        resp.setName(role.getName());
        resp.setPermissionIds(permissionIds);
        return resp;
    }
}

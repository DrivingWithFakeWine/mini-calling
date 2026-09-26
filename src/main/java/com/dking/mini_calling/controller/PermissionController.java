package com.dking.mini_calling.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dking.mini_calling.dto.PermissionResponse;
import com.dking.mini_calling.entity.SysPermission;
import com.dking.mini_calling.mapper.PermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "权限管理", description = "权限列表查询（供角色绑定权限使用）")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionMapper sysPermissionMapper;

    @Operation(summary = "查询全部权限", description = "需 permission:list 权限；返回 id/code/name 全量列表，id 用于角色绑定时提交 permissionIds")
    @GetMapping
    @PreAuthorize("hasAuthority('permission:list')")
    public List<PermissionResponse> list() {
        return sysPermissionMapper.selectList(
                        new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getId))
                .stream()
                .map(p -> {
                    PermissionResponse r = new PermissionResponse();
                    r.setId(p.getId());
                    r.setCode(p.getCode());
                    r.setName(p.getName());
                    return r;
                })
                .toList();
    }
}

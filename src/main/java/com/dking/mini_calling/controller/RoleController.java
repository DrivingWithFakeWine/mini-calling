package com.dking.mini_calling.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RolePageResponse;
import com.dking.mini_calling.dto.RoleUpdateRequest;
import com.dking.mini_calling.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "角色管理", description = "角色 CRUD 与权限绑定")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "分页查询角色", description = "需 role:list 权限")
    @GetMapping
    @PreAuthorize("hasAuthority('role:list')")
    public IPage<RolePageResponse> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword) {
        return roleService.pageRoles(page, size, keyword);
    }

    @Operation(summary = "新增角色", description = "需 role:create 权限，可同时绑定初始权限")
    @PostMapping
    @PreAuthorize("hasAuthority('role:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody RoleCreateRequest request) {
        roleService.createRole(request);
    }

    @Operation(summary = "编辑角色", description = "需 role:edit 权限，更新名称并重新绑定权限（先删后插）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable("id") Long id, @Valid @RequestBody RoleUpdateRequest request) {
        roleService.updateRole(id, request);
    }

    @Operation(summary = "删除角色", description = "需 role:delete 权限，同事务清理两张关联表；admin 角色禁止删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        roleService.deleteRole(id);
    }
}

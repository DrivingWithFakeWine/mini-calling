package com.dking.mini_calling.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.AssignRolesRequest;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户 CRUD 与密码管理")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户", description = "需 user:list 权限，支持按用户名/昵称模糊搜索")
    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public IPage<UserPageResponse> page(@RequestParam(name = "page", defaultValue = "1") long page,
                                        @RequestParam(name = "size", defaultValue = "10") long size,
                                        @RequestParam(name = "keyword", required = false) String keyword) {
        return userService.pageUsers(page, size, keyword);
    }

    @Operation(summary = "新增用户", description = "需 user:create 权限，密码用 BCrypt 加密入库")
    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
    }

    @Operation(summary = "删除用户", description = "需 user:delete 权限，逻辑删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        userService.deleteUser(id);
    }

    @Operation(summary = "修改本人密码", description = "无需额外权限，校验原密码后更新")
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal LoginUser loginUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(loginUser.getUserId(), request);
    }

    @Operation(summary = "为用户分配角色", description = "需 role:assign 权限，传空数组则清空该用户所有角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('role:assign')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request.getRoleIds());
    }
}


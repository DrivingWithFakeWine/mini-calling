package com.dking.mini_calling.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public IPage<UserPageResponse> page(@RequestParam(name = "page", defaultValue = "1") long page,
                                        @RequestParam(name = "size", defaultValue = "10") long size,
                                        @RequestParam(name = "keyword", required = false) String keyword) {
        return userService.pageUsers(page, size, keyword);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        userService.deleteUser(id);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal LoginUser loginUser,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(loginUser.getUserId(), request);
    }
}


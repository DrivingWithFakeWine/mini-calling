package com.dking.mini_calling.service.impl;

import com.dking.mini_calling.BaseIntegrationTest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Transactional
class UserServiceTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("创建用户成功，密码被 BCrypt 加密")
    void createUser_encodesPassword() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("plain123");
        req.setNickname("新用户");
        req.setRoleIds(java.util.List.of(2L));  // editor 角色，按你种子数据调整

        userService.createUser(req);

        var user = userService.getUserByUsername("newuser");  // 需暴露一个查询单用户的方法
        assertThat(user).isNotNull();
        assertThat(user.getPassword()).isNotEqualTo("plain123");
        assertThat(passwordEncoder.matches("plain123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("用户名重复时抛 BusinessException")
    void createUser_duplicateUsername_throws() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("zhangsan");   // 种子数据里已有
        req.setPassword("any123");
        req.setNickname("重复");

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("修改密码：原密码错误时抛异常")
    void changePassword_wrongOldPassword_throws() {
        // 动态查询，不写死 id=2；查不到时失败信息明确，而不是 NPE
        Long zhangsanId = Optional.ofNullable(userService.getUserByUsername("zhangsan"))
                .orElseThrow(() -> new IllegalStateException("种子数据缺少 zhangsan，检查 init SQL"))
                .getId();
        // 假设 zhangsan 种子密码是 123456，用 admin 的 userId 也行
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong_old");
        req.setNewPassword("new123456");

        assertThatThrownBy(() -> userService.changePassword(zhangsanId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码错误");
    }

    @Test
    @DisplayName("修改密码：正确原密码，改完后新密码可登录校验通过")
    void changePassword_success() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("123456");
        req.setNewPassword("new123456");

        Long zhangsanId = userService.getUserByUsername("zhangsan").getId();
        userService.changePassword(zhangsanId, req);

        var user = userService.getUserByUsername("zhangsan");
        assertThat(passwordEncoder.matches("new123456", user.getPassword())).isTrue();
    }
}

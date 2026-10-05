package com.dking.mini_calling.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.dto.ChangePasswordRequest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.mapper.UserMapper;
import com.dking.mini_calling.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

// service/impl/UserServiceImpl.java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<UserPageResponse> pageUsers(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId);

        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return result.convert(this::toResponse);
    }

    @Override
    public Long createUser(UserCreateRequest req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));   // 关键：入库前加密
        user.setNickname(req.getNickname());
        user.setEnabled(1);                                            // 按你实体类型调整
        userMapper.insert(user);

        if (req.getRoleIds() != null) {
            for (Long roleId : req.getRoleIds()) {
                userMapper.insertUserRole(user.getId(), roleId);
            }
        }
        return user.getId();   // MP insert 后主键已回填
    }

    @Override
    public void deleteUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        guardAdmin(user);
        userMapper.deleteById(id);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest req) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    private UserPageResponse toResponse(SysUser user) {
        UserPageResponse resp = new UserPageResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setEnabled(user.getEnabled());
        resp.setCreateTime(user.getCreateTime());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        guardAdmin(target);   // 改 admin 的角色 = 可能摘掉它的权限，一并禁止
        // 先清空再批量插入，幂等
        userMapper.deleteUserRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userMapper.batchInsertUserRoles(userId, roleIds);
        }
    }

    @Override
    public void setEnabled(Long userId, boolean enabled) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        guardAdmin(user);
        user.setEnabled(enabled ? 1 : 0);
        userMapper.updateById(user);
    }

    @Override
    public SysUser getUserByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Override
    public SysUser getUserById(Long id) {
        return userMapper.selectById(id);   // 逻辑删除的查不到，返回 null
    }

    /** 业务不变量：admin 是受保护账号，任何修改操作一律拒绝。
     *  放在 Service 层——REST、agent 工具、未来任何新入口都逃不过这道闸（Phase 5 修复自删漏洞） */
    private void guardAdmin(SysUser user) {
        if (user.getId() == 1L || "admin".equals(user.getUsername())) {
            throw new BusinessException("admin 是受保护账号，禁止此操作");
        }
    }
}


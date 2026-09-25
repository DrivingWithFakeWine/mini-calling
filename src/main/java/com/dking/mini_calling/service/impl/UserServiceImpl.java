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
import org.springframework.util.StringUtils;

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
    public void createUser(UserCreateRequest req) {
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
    }

    @Override
    public void deleteUser(Long id) {
        userMapper.deleteById(id);   // 逻辑删除，MP 自动改 deleted 字段
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
}


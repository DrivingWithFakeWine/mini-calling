package com.dking.mini_calling.security;

import com.dking.mini_calling.entity.SysPermission;
import com.dking.mini_calling.entity.SysRole;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.mapper.PermissionMapper;
import com.dking.mini_calling.mapper.RoleMapper;
import com.dking.mini_calling.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 查用户（用你之前 XML 里写好的方法）
        SysUser sysUser = userMapper.selectByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 2. 被禁用的账号直接拒绝——抛 DisabledException 而不是返回 null，
        //    前端能拿到明确的"账号已禁用"提示
        if (sysUser.getEnabled() == null || sysUser.getEnabled() != 1) {
            throw new DisabledException("账号已禁用");
        }

        // 3. 查角色 + 权限，统一塞进 authorities
        List<SysRole> roles = roleMapper.selectByUserId(sysUser.getId());
        List<SysPermission> permissions = permissionMapper.selectByUserId(sysUser.getId());

        List<SimpleGrantedAuthority> authorities = Stream.concat(
                        roles.stream().map(r -> new SimpleGrantedAuthority(r.getCode())),       // ROLE_ADMIN
                        permissions.stream().map(p -> new SimpleGrantedAuthority(p.getCode()))) // user:delete
                .distinct()
                .toList();

        // 4. 返回 LoginUser，password 字段是库里查出的 BCrypt 密文，
        //    框架拿到后会和用户输入的明文做 matches() 比对
        return new LoginUser(sysUser, authorities);
    }
}

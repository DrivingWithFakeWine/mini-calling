package com.dking.mini_calling.security;

import com.dking.mini_calling.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Spring Security 的认证主体
 * 继承 Security 的 User 获得标准认证结构，额外携带业务字段，
 * 后面签发 JWT、Controller 里获取当前用户都会用到
 */
@Getter
public class LoginUser extends User {

    private final Long userId;
    private final String nickname;

    public LoginUser(SysUser sysUser, Collection<? extends GrantedAuthority> authorities) {
        super(sysUser.getUsername(), sysUser.getPassword(), authorities);
        this.userId = sysUser.getId();
        this.nickname = sysUser.getNickname();
    }
}

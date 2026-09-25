package com.dking.mini_calling.filter;

import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.security.UserDetailsServiceImpl;
import com.dking.mini_calling.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    // 变更点 1：新增注入
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 无 Authorization 头或非 Bearer 前缀 → 直接放行（后续被 Security 拦截返回 401）
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        // 变更点 2：try 块整体替换
        try {
            Claims claims = jwtUtil.validateAndParse(token);
            String username = claims.getSubject();

            // 实时加载用户 + 权限（用户被删/禁用会抛 AuthenticationException，进 catch）
            LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            log.info("Filter 组装的 authorities = {}", loginUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException | JwtException | IllegalArgumentException e) {
            log.debug("认证信息无效: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

package com.dking.mini_calling.filter;

import com.dking.mini_calling.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
        try {
            Claims claims = jwtUtil.validateAndParse(token);
            String username = claims.getSubject();
            Long userId = claims.get("uid", Long.class);

            // 构建 Authentication 对象，credentials 传 null（JWT 无需密码）
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username, null, List.of());
            // 把 userId 塞到 details 里，Controller 可以通过 SecurityContextHolder 取到
            authentication.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("Token 已过期: {}", e.getMessage());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            log.debug("Token 无效: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

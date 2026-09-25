package com.dking.mini_calling.controller;

import com.dking.mini_calling.dto.LoginRequest;
import com.dking.mini_calling.dto.LoginResponse;
import com.dking.mini_calling.dto.UserInfoResponse;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private long expirationMs;

    /**
     * 认证失败会抛 BadCredentialsException，由 GlobalExceptionHandler 转 401
     * 认证成功返回 LoginUser，从中取出 userId 签发 token
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtUtil.generateToken(loginUser.getUsername(), loginUser.getUserId());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .build();
    }

    @GetMapping("/me")
    public UserInfoResponse me(@AuthenticationPrincipal LoginUser loginUser) {
        // authorities 是角色+权限码的混合集合，按前缀拆开：
        //   ROLE_ 开头 → 角色；其余 → 细粒度权限码
        List<String> authorities = loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        List<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))          // 去掉前缀，["ROLE_ADMIN"] → ["ADMIN"]
                .toList();
        List<String> permissions = authorities.stream()
                .filter(a -> !a.startsWith("ROLE_"))
                .toList();

        return UserInfoResponse.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}

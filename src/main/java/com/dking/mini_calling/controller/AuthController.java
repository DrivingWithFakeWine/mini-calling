package com.dking.mini_calling.controller;

import com.dking.mini_calling.dto.LoginRequest;
import com.dking.mini_calling.dto.LoginResponse;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        String token = jwtUtil.generateToken(loginUser.getUsername(), loginUser.getUserId());

        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expirationMs / 1000)
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .build());
    }
}

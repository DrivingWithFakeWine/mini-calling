package com.dking.mini_calling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize，下一步做功能权限（接口级鉴权）时直接可用
public class SecurityConfig {

    /** 必须有这个 Bean，登录时框架才能用 BCrypt 比对库里那串密文 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // 纯 API 服务，无 Cookie 会话，CSRF 暂关
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/actuator/health").permitAll()  // /auth/login 给下一步登录接口预留
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态，为 JWT 铺路
                .httpBasic(Customizer.withDefaults())   // 本步临时用 Basic 认证做验证，JWT 步会移除
                .formLogin(form -> form.disable());
        return http.build();
    }
}

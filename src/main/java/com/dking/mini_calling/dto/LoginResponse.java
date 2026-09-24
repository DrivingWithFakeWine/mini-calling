package com.dking.mini_calling.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;     // "Bearer"
    private Long expiresIn;       // 秒
    private Long userId;
    private String username;
}

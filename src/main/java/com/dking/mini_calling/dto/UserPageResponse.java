package com.dking.mini_calling.dto;

import lombok.Data;

import java.time.LocalDateTime;

// dto/UserPageResponse.java —— 刻意不含 password！
@Data
public class UserPageResponse {
    private Long id;
    private String username;
    private String nickname;
    private Integer enabled;
    private LocalDateTime createTime;   // 字段名按你实体实际调整 按照实体SysUser来写
}

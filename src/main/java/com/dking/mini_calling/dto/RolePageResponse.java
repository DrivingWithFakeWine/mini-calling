package com.dking.mini_calling.dto;

import lombok.Data;

import java.util.List;

@Data
public class RolePageResponse {
    private Long id;
    private String code;
    private String name;
    private List<Long> permissionIds;
}

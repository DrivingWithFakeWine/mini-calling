package com.dking.mini_calling.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RoleUpdateRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private List<Long> permissionIds;
}

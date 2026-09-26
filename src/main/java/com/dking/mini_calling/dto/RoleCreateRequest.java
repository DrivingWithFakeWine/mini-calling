package com.dking.mini_calling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/*
创建角色  请求
 */
@Data
public class RoleCreateRequest {
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9:_-]*$", message = "编码需小写字母开头，可含小写字母/数字/冒号/横线")
    private String code;
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private List<Long> permissionIds;   // 可空 = 不绑权限
}

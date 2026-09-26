package com.dking.mini_calling.dto;

import lombok.Data;

import java.util.List;

/*
    分配角色
 */

// dto/AssignRolesRequest.java
@Data
public class AssignRolesRequest {
    private List<Long> roleIds;   // 传空数组 = 清空用户的所有角色
}

package com.dking.mini_calling.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data   // lombok的注解，减少编码
@TableName("sys_user_role")
public class SysUserRole {

    private Long RoleId;

    private Long PermissionId;
}

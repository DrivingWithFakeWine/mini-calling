package com.dking.mini_calling.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)      // 数据库自增主键
    private Long id;
    private String name;
    private String code;
    private String description;
    private LocalDateTime createTime;
}

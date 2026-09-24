package com.dking.mini_calling.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)      // 数据库自增主键
    private Long id;
    private String name;
    private String code;
    private Integer type;      // 1-菜单 2-按钮 3-接口
    private String url;
    private Long parentId;
}

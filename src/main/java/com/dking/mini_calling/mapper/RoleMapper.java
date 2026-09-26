package com.dking.mini_calling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dking.mini_calling.entity.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {
    /** 查询单个角色的权限 ID 列表 */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 清空角色的所有权限绑定（编辑角色时"先删后插"用） */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissionByRoleId(@Param("roleId") Long roleId);

    /** 清空角色的所有用户绑定（删除角色前清理，防孤儿数据） */
    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteUserRoleByRoleId(@Param("roleId") Long roleId);

    /** 查某用户拥有的所有角色（经 sys_user_role 关联） */
    List<SysRole> selectByUserId(Long userId);

    /** 批量查询多个角色的权限 ID（分页时避免 N+1） */
    @Select("<script>" +
            "SELECT role_id, permission_id FROM sys_role_permission WHERE role_id IN " +
            "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>" +
            "#{rid}" +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectPermissionsByRoleIds(@Param("roleIds") List<Long> roleIds);

    /** 批量插入角色-权限关联（<script>+<foreach> 拼一条多值 INSERT，比循环单条快得多） */
    @Insert("<script>" +
            "INSERT INTO sys_role_permission(role_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='pid' separator=','>" +
            "(#{roleId}, #{pid})" +
            "</foreach>" +
            "</script>")
    int batchInsertRolePermission(@Param("roleId") Long roleId,
                                  @Param("permissionIds") List<Long> permissionIds);
}

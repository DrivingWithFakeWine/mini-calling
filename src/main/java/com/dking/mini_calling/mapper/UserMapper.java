package com.dking.mini_calling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dking.mini_calling.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
    /** 按用户名查（登录用），自动过滤已逻辑删除的 */
    SysUser selectByUsername(String username);

    /** 按 id 查 */
    SysUser selectById(Long id);

    @Insert("INSERT INTO sys_user_role(user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    // UserMapper 或单独的 PermissionMapper 里加
//    @Select("SELECT DISTINCT p.code FROM sys_permission p " +
//            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
//            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
//            "WHERE ur.user_id = #{userId}")
//    List<String> selectPermCodesByUserId(Long userId);

    // 查角色编码
    @Select("SELECT r.code FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(Long userId);

    //删除用户 拥有的所有角色
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRolesByUserId(@Param("userId") Long userId);


    //给用户  批量插入 角色
    @Insert("<script>" +
            "INSERT INTO sys_user_role(user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='rid' separator=','>" +
            "(#{userId}, #{rid})" +
            "</foreach>" +
            "</script>")
    int batchInsertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}

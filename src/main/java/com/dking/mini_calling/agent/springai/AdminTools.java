package com.dking.mini_calling.agent.springai;

import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RolePageResponse;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.service.RoleService;
import com.dking.mini_calling.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 框架版的管理工具集（与手写版 UserAdminTools/RoleAdminTools 功能一一对应）。
 *
 * 对比观察点：
 * - 手写版的 ToolDefinition 手写 JSON Schema → 这里由 @Tool/@ToolParam 注解自动生成；
 * - 手写版的 ToolArgs 防御取值 → 这里由框架把 arguments 绑定成方法参数；
 * - 不变的部分：权限校验、admin 硬护栏、返回精简 JSON——安全是业务决策，框架不替你做。
 */
@Component
public class AdminTools {

    private final UserService userService;
    private final RoleService roleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminTools(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    // ─── 身份与权限（对应手写版 ToolContext + ToolRegistry.hasPermission） ───

    private LoginUser caller(ToolContext toolContext) {
        Object caller = toolContext.getContext().get("loginUser");
        return caller instanceof LoginUser user ? user : null;
    }

    private boolean hasPermission(ToolContext toolContext, String permission) {
        LoginUser user = caller(toolContext);
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }

    private String deny(String permission) {
        return "{\"error\":\"无权限执行（需要 " + permission + "）\"}";
    }

    /** 对应手写版 UserAdminTools.guardAdmin：admin 禁止禁用/删除/改角色 */
    private String guardAdmin(Long userId) {
        SysUser user = userService.getUserById(userId);
        if (user != null && (user.getId() == 1L || "admin".equals(user.getUsername()))) {
            return "{\"error\":\"admin 是受保护账号，禁止此操作\"}";
        }
        return null;
    }

    // ─── 用户工具 ───

    @Tool(name = "search_users", description = "分页查询系统用户，支持按用户名/昵称模糊搜索。当用户询问有哪些用户、查找某个用户时使用")
    public String searchUsers(
            @ToolParam(required = false, description = "用户名或昵称的模糊关键字，可省略") String keyword,
            @ToolParam(required = false, description = "页码，默认 1") Integer page,
            @ToolParam(required = false, description = "每页条数，默认 10，最大 50") Integer size,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "user:list")) {
            return deny("user:list");
        }
        try {
            IPage<UserPageResponse> result = userService.pageUsers(
                    page == null ? 1 : page, size == null ? 10 : Math.min(size, 50), keyword);
            List<Map<String, Object>> users = result.getRecords().stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("nickname", u.getNickname());
                m.put("enabled", u.getEnabled());
                return m;
            }).toList();
            return objectMapper.writeValueAsString(
                    Map.of("total", result.getTotal(), "page", result.getCurrent(), "users", users));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(name = "create_user", description = "创建新用户（用户名全局唯一，密码长度 6-32 位）。仅当用户明确要求创建账号并提供用户名和密码时使用")
    public String createUser(
            @ToolParam(description = "登录用户名，全局唯一") String username,
            @ToolParam(description = "初始密码，6-32 位") String password,
            @ToolParam(required = false, description = "昵称，可省略") String nickname,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "user:create")) {
            return deny("user:create");
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return "{\"error\":\"username 和 password 必填\"}";
        }
        if (password.length() < 6 || password.length() > 32) {
            return "{\"error\":\"密码长度需 6-32 位\"}";
        }
        try {
            UserCreateRequest req = new UserCreateRequest();
            req.setUsername(username);
            req.setPassword(password);
            req.setNickname(nickname);
            Long id = userService.createUser(req);
            return objectMapper.writeValueAsString(Map.of("id", id, "username", username));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(name = "set_user_enabled", description = "启用或禁用用户账号，禁用后该用户无法登录。admin 是受保护账号，禁止禁用")
    public String setUserEnabled(
            @ToolParam(description = "目标用户 id") Long userId,
            @ToolParam(description = "true=启用，false=禁用") Boolean enabled,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "user:update")) {
            return deny("user:update");
        }
        String denied = guardAdmin(userId);
        if (denied != null) {
            return denied;
        }
        if (userId == null || enabled == null) {
            return "{\"error\":\"userId 与 enabled 必填\"}";
        }
        try {
            userService.setEnabled(userId, enabled);
            return objectMapper.writeValueAsString(Map.of("userId", userId, "enabled", enabled));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(name = "delete_user", description = "删除用户（逻辑删除，数据保留删除标记）。高风险操作：仅当用户明确要求删除某个用户时使用；admin 受保护禁止删除")
    public String deleteUser(
            @ToolParam(description = "要删除的用户 id") Long userId,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "user:delete")) {
            return deny("user:delete");
        }
        String denied = guardAdmin(userId);
        if (denied != null) {
            return denied;
        }
        if (userId == null) {
            return "{\"error\":\"userId 必填\"}";
        }
        try {
            userService.deleteUser(userId);
            return objectMapper.writeValueAsString(Map.of("deleted", true, "userId", userId));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(name = "assign_roles", description = "重设用户的角色列表（完全替换，不是追加）。roleIds 传空数组表示清空该用户所有角色。admin 受保护，禁止改其角色")
    public String assignRoles(
            @ToolParam(description = "目标用户 id") Long userId,
            @ToolParam(description = "完整的角色 id 列表，空数组=清空所有角色") List<Long> roleIds,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "role:assign")) {
            return deny("role:assign");
        }
        String denied = guardAdmin(userId);
        if (denied != null) {
            return denied;
        }
        if (userId == null || roleIds == null) {
            return "{\"error\":\"userId 与 roleIds 必填（清空角色请传空数组）\"}";
        }
        try {
            userService.assignRoles(userId, roleIds);
            return objectMapper.writeValueAsString(Map.of("userId", userId, "roleIds", roleIds));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ─── 角色工具 ───

    @Tool(name = "list_roles", description = "查询系统全部角色，含每个角色绑定的权限 id 列表。当用户询问有哪些角色时使用")
    public String listRoles(ToolContext toolContext) {
        if (!hasPermission(toolContext, "role:list")) {
            return deny("role:list");
        }
        try {
            List<RolePageResponse> roles = roleService.pageRoles(1, 50, null).getRecords();
            List<Map<String, Object>> data = roles.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("code", r.getCode());
                m.put("name", r.getName());
                m.put("permissionIds", r.getPermissionIds());
                return m;
            }).toList();
            return objectMapper.writeValueAsString(Map.of("roles", data));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Tool(name = "create_role", description = "创建新角色，可同时绑定权限。code 必须小写字母开头（如 ops_admin）。仅当用户明确要求创建角色时使用")
    public String createRole(
            @ToolParam(description = "角色名称，如 运维管理员") String name,
            @ToolParam(description = "角色编码，小写字母开头，如 ops_admin") String code,
            @ToolParam(required = false, description = "初始权限 id 列表，可省略") List<Long> permissionIds,
            ToolContext toolContext) {
        if (!hasPermission(toolContext, "role:create")) {
            return deny("role:create");
        }
        if (!StringUtils.hasText(name) || !StringUtils.hasText(code)) {
            return "{\"error\":\"name 与 code 必填\"}";
        }
        if (!code.matches("^[a-z][a-z0-9:_-]*$")) {
            return "{\"error\":\"code 需小写字母开头，可含小写字母/数字/冒号/横线\"}";
        }
        try {
            RoleCreateRequest req = new RoleCreateRequest();
            req.setName(name);
            req.setCode(code);
            req.setPermissionIds(permissionIds == null ? List.of() : permissionIds);
            Long id = roleService.createRole(req);
            return objectMapper.writeValueAsString(Map.of("id", id, "code", code, "name", name));
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}

package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.dto.UserPageResponse;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.service.UserService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理工具集：agent 的"真家伙"，直接调用 UserService 操作数据库。
 *
 * 两条设计原则：
 * 1. 返回给模型的信息最小够用（精简 Map，绝不含 password/createTime）——既控制 token，也防止
 *    敏感字段进入提示词上下文；
 * 2. admin 是硬护栏（代码层，不是提示词层）：禁用/删除/改角色一律拒绝，防模型犯错也防注入操纵。
 */
public class UserAdminTools {

    /** 硬护栏：这三个操作对 admin 一票否决。异常会被 ToolRegistry 转成 {"error":...} 回传模型 */
    static void guardAdmin(UserService userService, Long userId) {
        SysUser user = userService.getUserById(userId);
        if (user != null && (user.getId() == 1L || "admin".equals(user.getUsername()))) {
            throw new IllegalArgumentException("admin 是受保护账号，禁止此操作");
        }
    }

    @Component
    public static class SearchUsersTool implements AgentTool {

        private final UserService userService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public SearchUsersTool(UserService userService) {
            this.userService = userService;
        }

        @Override
        public String name() {
            return "search_users";
        }

        @Override
        public String description() {
            return "分页查询系统用户，支持按用户名/昵称模糊搜索。当用户询问有哪些用户、查找某个用户时使用";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "keyword", Map.of("type", "string", "description", "用户名或昵称的模糊关键字，可省略"),
                            "page", Map.of("type", "integer", "description", "页码，默认 1"),
                            "size", Map.of("type", "integer", "description", "每页条数，默认 10，最大 50")));
        }

        @Override
        public String requiredPermission() {
            return "user:list";
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            int page = ToolArgs.intOrDefault(args, "page", 1);
            int size = Math.min(ToolArgs.intOrDefault(args, "size", 10), 50);
            IPage<UserPageResponse> result = userService.pageUsers(page, size, ToolArgs.text(args, "keyword"));

            // 信息密度原则：只给模型 id/用户名/昵称/状态，够推理即可
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
        }
    }

    @Component
    public static class CreateUserTool implements AgentTool {

        private final UserService userService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public CreateUserTool(UserService userService) {
            this.userService = userService;
        }

        @Override
        public String name() {
            return "create_user";
        }

        @Override
        public String description() {
            return "创建新用户（用户名全局唯一，密码长度 6-32 位）。仅当用户明确要求创建账号并提供用户名和密码时使用。"
                    + "角色处理：用户提到角色名称时，先调用 list_roles 查到对应 id 再传 roleIds，不要猜 id；"
                    + "用户未提角色时，先展示角色列表询问是否分配，确认后再调用本工具（不分配则省略 roleIds）";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "username", Map.of("type", "string", "description", "登录用户名，全局唯一"),
                            "password", Map.of("type", "string", "description", "初始密码，6-32 位"),
                            "nickname", Map.of("type", "string", "description", "昵称，可省略"),
                            "roleIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                                    "description", "初始角色 id 列表，可省略")),
                    "required", List.of("username", "password"));
        }

        @Override
        public String requiredPermission() {
            return "user:create";
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            String username = ToolArgs.text(args, "username");
            String password = ToolArgs.text(args, "password");
            // Controller 路径靠 @Valid 校验，工具路径没有那道门——自己补上，否则模型传个 2 位密码也照收
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalArgumentException("username 和 password 必填");
            }
            if (password.length() < 6 || password.length() > 32) {
                throw new IllegalArgumentException("密码长度需 6-32 位");
            }

            UserCreateRequest req = new UserCreateRequest();
            req.setUsername(username);
            req.setPassword(password);
            req.setNickname(ToolArgs.text(args, "nickname"));
            req.setRoleIds(ToolArgs.longList(args, "roleIds"));

            Long id = userService.createUser(req);
            return objectMapper.writeValueAsString(Map.of("id", id, "username", username));
        }
    }

    @Component
    public static class SetUserEnabledTool implements AgentTool {

        private final UserService userService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public SetUserEnabledTool(UserService userService) {
            this.userService = userService;
        }

        @Override
        public String name() {
            return "set_user_enabled";
        }

        @Override
        public String description() {
            return "启用或禁用用户账号，禁用后该用户无法登录。admin 是受保护账号，禁止禁用";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "integer", "description", "目标用户 id"),
                            "enabled", Map.of("type", "boolean", "description", "true=启用，false=禁用")),
                    "required", List.of("userId", "enabled"));
        }

        @Override
        public String requiredPermission() {
            return "user:update";
        }

        @Override
        public boolean needConfirm() {
            return true;   // 禁用账号影响他人登录，执行前必须人工确认
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            Long userId = ToolArgs.longValue(args, "userId");
            JsonNode enabled = args.get("enabled");
            if (userId == null || enabled == null || !enabled.isBoolean()) {
                throw new IllegalArgumentException("userId 与 enabled(boolean) 必填");
            }
            guardAdmin(userService, userId);

            userService.setEnabled(userId, enabled.asBoolean());
            return objectMapper.writeValueAsString(
                    Map.of("userId", userId, "enabled", enabled.asBoolean()));
        }
    }

    @Component
    public static class DeleteUserTool implements AgentTool {

        private final UserService userService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public DeleteUserTool(UserService userService) {
            this.userService = userService;
        }

        @Override
        public String name() {
            return "delete_user";
        }

        @Override
        public String description() {
            return "删除用户（逻辑删除，数据保留删除标记）。高风险操作：仅当用户明确要求删除某个用户时使用；admin 受保护禁止删除";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of("userId", Map.of("type", "integer", "description", "要删除的用户 id")),
                    "required", List.of("userId"));
        }

        @Override
        public String requiredPermission() {
            return "user:delete";
        }

        @Override
        public boolean needConfirm() {
            return true;   // 删除是破坏性操作，执行前必须人工确认
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            Long userId = ToolArgs.longValue(args, "userId");
            if (userId == null) {
                throw new IllegalArgumentException("userId 必填");
            }
            guardAdmin(userService, userId);

            userService.deleteUser(userId);
            return objectMapper.writeValueAsString(Map.of("deleted", true, "userId", userId));
        }
    }

    @Component
    public static class AssignRolesTool implements AgentTool {

        private final UserService userService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public AssignRolesTool(UserService userService) {
            this.userService = userService;
        }

        @Override
        public String name() {
            return "assign_roles";
        }

        @Override
        public String description() {
            return "重设用户的角色列表（完全替换，不是追加）。roleIds 传空数组表示清空该用户所有角色。admin 受保护，禁止改其角色";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "userId", Map.of("type", "integer", "description", "目标用户 id"),
                            "roleIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                                    "description", "完整的角色 id 列表，空数组=清空所有角色")),
                    "required", List.of("userId", "roleIds"));
        }

        @Override
        public String requiredPermission() {
            return "role:assign";
        }

        @Override
        public boolean needConfirm() {
            return true;   // 角色变更直接影响权限边界，执行前必须人工确认
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            Long userId = ToolArgs.longValue(args, "userId");
            if (userId == null || !ToolArgs.has(args, "roleIds")) {
                throw new IllegalArgumentException("userId 与 roleIds 必填（清空角色请传空数组）");
            }
            guardAdmin(userService, userId);

            List<Long> roleIds = ToolArgs.longList(args, "roleIds");
            userService.assignRoles(userId, roleIds);
            return objectMapper.writeValueAsString(Map.of("userId", userId, "roleIds", roleIds));
        }
    }
}

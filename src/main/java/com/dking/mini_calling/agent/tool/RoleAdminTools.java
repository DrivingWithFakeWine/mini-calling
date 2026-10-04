package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.dto.RoleCreateRequest;
import com.dking.mini_calling.dto.RolePageResponse;
import com.dking.mini_calling.service.RoleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理工具集。查询类工具无副作用；创建类工具的入参校验在工具层自建
 * （Controller 的 @Valid 注解只对 HTTP 入口生效，工具路径没有那道门）
 */
public class RoleAdminTools {

    @Component
    public static class ListRolesTool implements AgentTool {

        private final RoleService roleService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public ListRolesTool(RoleService roleService) {
            this.roleService = roleService;
        }

        @Override
        public String name() {
            return "list_roles";
        }

        @Override
        public String description() {
            return "查询系统全部角色，含每个角色绑定的权限 id 列表。当用户询问有哪些角色时使用";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String requiredPermission() {
            return "role:list";
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
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
        }
    }

    @Component
    public static class CreateRoleTool implements AgentTool {

        private static final String CODE_PATTERN = "^[a-z][a-z0-9:_-]*$";

        private final RoleService roleService;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public CreateRoleTool(RoleService roleService) {
            this.roleService = roleService;
        }

        @Override
        public String name() {
            return "create_role";
        }

        @Override
        public String description() {
            return "创建新角色，可同时绑定权限。code 必须小写字母开头（如 ops_admin）。仅当用户明确要求创建角色时使用";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "name", Map.of("type", "string", "description", "角色名称，如 运维管理员"),
                            "code", Map.of("type", "string", "description", "角色编码，小写字母开头，如 ops_admin"),
                            "permissionIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                                    "description", "初始权限 id 列表，可省略")),
                    "required", List.of("name", "code"));
        }

        @Override
        public String requiredPermission() {
            return "role:create";
        }

        @Override
        public String execute(JsonNode args, ToolContext ctx) throws Exception {
            String name = ToolArgs.text(args, "name");
            String code = ToolArgs.text(args, "code");
            if (!StringUtils.hasText(name) || !StringUtils.hasText(code)) {
                throw new IllegalArgumentException("name 与 code 必填");
            }
            if (!code.matches(CODE_PATTERN)) {
                throw new IllegalArgumentException("code 需小写字母开头，可含小写字母/数字/冒号/横线");
            }

            RoleCreateRequest req = new RoleCreateRequest();
            req.setName(name);
            req.setCode(code);
            req.setPermissionIds(ToolArgs.longList(args, "permissionIds"));

            Long id = roleService.createRole(req);
            return objectMapper.writeValueAsString(Map.of("id", id, "code", code, "name", name));
        }
    }
}

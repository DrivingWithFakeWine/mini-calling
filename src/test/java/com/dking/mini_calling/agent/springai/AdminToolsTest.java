package com.dking.mini_calling.agent.springai;

import com.dking.mini_calling.BaseIntegrationTest;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring AI 框架版工具的集成测试：不经过 LLM，直接调 @Tool 方法，
 * 验证"同一套安全语义在框架版里同样成立"——权限、admin 护栏、逻辑删除。
 * 有了这一层，两个版本的行为对比才公平。
 */
class AdminToolsTest extends BaseIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private AdminTools tools;
    @Autowired
    private UserService userService;

    private ToolContext ctx(String... permissions) {
        SysUser u = new SysUser();
        u.setId(99L);
        u.setUsername("tester");
        u.setPassword("x");
        return new ToolContext(Map.of("loginUser",
                new LoginUser(u, Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList())));
    }

    private Long extractId(String json) {
        try {
            return new ObjectMapper().readTree(json).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("响应不是预期 JSON: " + json, e);
        }
    }

    @Test
    @DisplayName("正例：search_users 返回精简列表，绝不含 password 字段")
    void searchUsers_slimAndSafe() {
        String out = tools.searchUsers("admin", null, null, ctx("user:list"));

        assertThat(out).contains("admin").doesNotContain("password");
    }

    @Test
    @DisplayName("反例：无 user:list 权限 → 权限错误 JSON，与手写版语义一致")
    void searchUsers_permissionDenied() {
        String out = tools.searchUsers(null, null, null, ctx("ROLE_USER"));

        assertThat(out).contains("无权限").contains("user:list");
    }

    @Test
    @DisplayName("正例：create_user 落库成功并返回新 id")
    void createUser_persists() {
        String out = tools.createUser("springai_u1", "pass123456", "框架用户", ctx("user:create"));

        assertThat(out).contains("springai_u1");
        assertThat(userService.getUserById(extractId(out))).isNotNull();
    }

    @Test
    @DisplayName("反例：admin 对 set_user_enabled 受硬护栏保护（Spring AI 版同样拦截）")
    void adminProtected_atFrameworkTools() {
        String out = tools.setUserEnabled(1L, false, ctx("user:update"));

        assertThat(out).contains("受保护");
        assertThat(userService.getUserById(1L)).isNotNull();
    }

    @Test
    @DisplayName("正例：list_roles 返回角色与权限 id")
    void listRoles_works() {
        String out = tools.listRoles(ctx("role:list"));

        assertThat(out).contains("admin");
    }
}

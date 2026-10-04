package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.BaseIntegrationTest;
import com.dking.mini_calling.dto.UserCreateRequest;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.security.LoginUser;
import com.dking.mini_calling.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实工具 × 真实数据库（Testcontainers MySQL）的集成测试：
 * 验证工具真的动了库、权限真的拦住了人、admin 真的受保护。
 * LLM 不参与——工具执行是纯 Java 调用，直接走 ToolRegistry.invoke 即可。
 * BaseIntegrationTest 的 @Transactional 保证每个用例的库变更自动回滚。
 */
class AgentToolsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ToolRegistry registry;
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper json = new ObjectMapper();

    /** 构造一个带指定权限码的调用者上下文 */
    private ToolContext ctx(String... permissions) {
        SysUser u = new SysUser();
        u.setId(99L);
        u.setUsername("tester");
        u.setPassword("x");
        return new ToolContext(new LoginUser(u,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    private String invoke(String tool, String argsJson, ToolContext ctx) {
        return registry.invoke(tool, argsJson, ctx);
    }

    private Long createUserViaService(String username) {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername(username);
        req.setPassword("pass123456");
        return userService.createUser(req);
    }

    @Test
    @DisplayName("正例：search_users 返回精简列表，绝不含 password 字段")
    void search_users_slimAndSafe() throws Exception {
        String out = invoke("search_users", "{\"keyword\":\"admin\"}", ctx("user:list"));

        JsonNode node = json.readTree(out);
        assertThat(node.has("error")).isFalse();
        assertThat(node.get("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(node.get("users").get(0).get("username").asText()).isEqualTo("admin");
        assertThat(out).doesNotContain("password");   // 密码字段绝不能进入提示词上下文
    }

    @Test
    @DisplayName("正例：create_user 落库成功并返回新 id")
    void create_user_persists() throws Exception {
        String out = invoke("create_user",
                "{\"username\":\"agent_u1\",\"password\":\"pass123456\",\"nickname\":\"工具人\"}",
                ctx("user:create"));

        JsonNode node = json.readTree(out);
        assertThat(node.has("error")).isFalse();
        SysUser user = userService.getUserById(node.get("id").asLong());
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("agent_u1");
    }

    @Test
    @DisplayName("正例：set_user_enabled 禁用后再启用，DB 状态随之变化")
    void set_user_enabled_roundtrip() throws Exception {
        Long id = createUserViaService("agent_u2");

        String out = invoke("set_user_enabled", "{\"userId\":" + id + ",\"enabled\":false}", ctx("user:update"));
        assertThat(json.readTree(out).has("error")).isFalse();
        assertThat(userService.getUserById(id).getEnabled()).isEqualTo(0);

        out = invoke("set_user_enabled", "{\"userId\":" + id + ",\"enabled\":true}", ctx("user:update"));
        assertThat(json.readTree(out).has("error")).isFalse();
        assertThat(userService.getUserById(id).getEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("正例：delete_user 逻辑删除，库中查不到了")
    void delete_user_logical() throws Exception {
        Long id = createUserViaService("agent_u3");

        String out = invoke("delete_user", "{\"userId\":" + id + "}", ctx("user:delete"));

        assertThat(json.readTree(out).has("error")).isFalse();
        assertThat(userService.getUserById(id)).isNull();
    }

    @Test
    @DisplayName("正例：assign_roles 完全替换语义——先绑 1 个角色再清空，关联表随之增减")
    void assign_roles_replacesLinks() throws Exception {
        Long id = createUserViaService("agent_u4");
        ToolContext c = ctx("role:assign");

        invoke("assign_roles", "{\"userId\":" + id + ",\"roleIds\":[1]}", c);
        Integer links = jdbcTemplate.queryForObject(
                "select count(*) from sys_user_role where user_id=?", Integer.class, id);
        assertThat(links).isEqualTo(1);

        invoke("assign_roles", "{\"userId\":" + id + ",\"roleIds\":[]}", c);   // 空数组=清空
        links = jdbcTemplate.queryForObject(
                "select count(*) from sys_user_role where user_id=?", Integer.class, id);
        assertThat(links).isZero();
    }

    @Test
    @DisplayName("反例：无 user:create 权限的调用者被拒，且数据库无副作用")
    void permissionDenied_noSideEffect() throws Exception {
        String out = invoke("create_user",
                "{\"username\":\"agent_no\",\"password\":\"pass123456\"}", ctx("ROLE_USER"));

        assertThat(out).contains("无权限").contains("user:create");
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_user where username='agent_no'", Integer.class);
        assertThat(count).isZero();   // 被拒的操作不能留下任何痕迹
    }

    @Test
    @DisplayName("反例：admin 受硬护栏保护——删除/禁用/改角色全部拒绝，账号完好")
    void adminProtected_byHardGuard() throws Exception {
        ToolContext admin = ctx("user:delete", "user:update", "role:assign");

        assertThat(invoke("delete_user", "{\"userId\":1}", admin)).contains("受保护");
        assertThat(invoke("set_user_enabled", "{\"userId\":1,\"enabled\":false}", admin)).contains("受保护");
        assertThat(invoke("assign_roles", "{\"userId\":1,\"roleIds\":[]}", admin)).contains("受保护");

        assertThat(userService.getUserById(1L)).isNotNull();   // admin 安然无恙
    }

    @Test
    @DisplayName("反例：目标用户不存在 → 错误回传给模型而不是异常上抛")
    void userNotFound_becomesErrorJson() throws Exception {
        String out = invoke("set_user_enabled", "{\"userId\":99999,\"enabled\":false}", ctx("user:update"));

        assertThat(out).contains("error").contains("用户不存在");
    }
}

package com.dking.mini_calling.auth;

import com.dking.mini_calling.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// 或用 @SpringBootTest + @AutoConfigureMockMvc 走全链路（推荐，能覆盖 JwtAuthenticationFilter）
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthFlowTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 登录并从统一响应体里取 token */
    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("token").asText();
    }

    @Test
    @DisplayName("登录成功返回 token，带上 token 能访问受保护接口")
    void login_thenAccessProtected() throws Exception {
        // 调用函数，获得token
        String token = login("admin", "admin123");

        mockMvc.perform(MockMvcRequestBuilders.get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").exists());
    }

    @Test
    @DisplayName("无 token 访问受保护接口返回 401 统一结构")
    void accessWithoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("无权限用户访问受保护接口返回 403 统一结构")
    void forbiddenUser_returns403() throws Exception {
        // 前提：测试库种子里的 zhangsan 是普通用户，没有 user:list 权限
        String token = login("zhangsan", "123456");   // 按你的种子密码调整

        mockMvc.perform(MockMvcRequestBuilders.get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}


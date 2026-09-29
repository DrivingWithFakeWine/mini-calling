package com.dking.mini_calling.agent.client;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * LlmClient 单元测试：MockRestServiceServer 绑定在 RestClient 上，
 * 既断言"发出去的请求长什么样"，也模拟上游各种响应，全程不触网
 */
class LlmClientTest {

    private static final String CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        // 复用生产代码的 baseRestClient 接线（baseUrl + 鉴权头），只有超时工厂留在生产构造器里
        AgentProperties props = new AgentProperties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmClient(LlmClient.baseRestClient(builder, props).build());
    }

    @AfterEach
    void verifyAllRequestsMatched() {
        server.verify();
    }

    private ChatRequest request(String userMessage) {
        AgentProperties props = new AgentProperties();   // 默认值：glm-4.6 / dummy-key / thinking 关闭
        return ChatRequest.of(props, List.of(
                ChatMessage.system("你是管理助手"),
                ChatMessage.user(userMessage)));
    }

    @Test
    @DisplayName("正例：请求携带 model/消息数组/鉴权头/thinking 关闭，响应正确解析出回答和 token 用量")
    void chat_parsesContentAndUsage() {
        server.expect(once(), requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AUTHORIZATION, "Bearer dummy-key"))
                .andExpect(jsonPath("$.model").value("glm-4.6"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("你好"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andRespond(withSuccess("""
                        {"id":"c1","model":"glm-4.6","choices":[{"index":0,"message":{"role":"assistant","content":"你好，我是管理助手"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                        """, MediaType.APPLICATION_JSON));

        ChatResponse response = client.chat(request("你好"));

        assertThat(response.firstContent()).isEqualTo("你好，我是管理助手");
        assertThat(response.model()).isEqualTo("glm-4.6");
        assertThat(response.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    @DisplayName("反例：上游 401 → 转译为友好的 BusinessException")
    void chat_translatesInvalidKey() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"1002\",\"message\":\"Invalid API key\"}}"));

        assertThatThrownBy(() -> client.chat(request("你好")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    @DisplayName("反例：finish_reason=sensitive（智谱内容安全拦截，HTTP 仍是 200）→ 明确的业务提示")
    void chat_translatesSensitiveBlock() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("""
                        {"id":"c2","choices":[{"index":0,"message":{"role":"assistant","content":null},"finish_reason":"sensitive"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(request("敏感问题")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全");
    }

    @Test
    @DisplayName("反例：读超时/网络异常 → 转译为 BusinessException 而不是底层 IOException")
    void chat_translatesTimeout() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.chat(request("你好")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时");
    }
}

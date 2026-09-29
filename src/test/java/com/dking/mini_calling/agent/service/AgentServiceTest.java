package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.memory.ChatMemoryStore;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentService 单元测试：Mockito 模拟 LlmClient，重点验证
 * "第二轮请求的消息数组里真的带着第一轮的问答"——这就是多轮记忆的证明
 */
class AgentServiceTest {

    private LlmClient llmClient;
    private AgentProperties props;
    private ChatMemoryStore memoryStore;
    private AgentService service;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        memoryStore = new ChatMemoryStore(props);
        llmClient = mock(LlmClient.class);
        service = new AgentService(props, llmClient, memoryStore);
    }

    private LoginUser admin() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("x");
        return new LoginUser(u, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private ChatResponse resp(String text) {
        return new ChatResponse("id-1", "glm-4.6",
                List.of(new ChatResponse.Choice(new ChatMessage("assistant", text, null, null), "stop")),
                new ChatResponse.Usage(10, 5, 15));
    }

    @Test
    @DisplayName("正例-首轮：sessionId 为空时创建会话，LLM 收到 system+user 两条消息")
    void firstRound_createsSession() {
        when(llmClient.chat(any())).thenReturn(resp("你好，管理员"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("你好", null));

        assertThat(out.sessionId()).startsWith("1-");   // userId 前缀
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(captor.capture());
        var messages = captor.getValue().messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1).content()).isEqualTo("你好");
    }

    @Test
    @DisplayName("正例-第二轮：请求体携带第一轮的 user+assistant 消息，共 4 条")
    void secondRound_carriesHistory() {
        when(llmClient.chat(any())).thenReturn(resp("你好，我是助手"), resp("你叫小明"));

        ChatWebResponse first = service.chat(admin(), new ChatWebRequest("你好", null));
        service.chat(admin(), new ChatWebRequest("我叫什么", first.sessionId()));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient, times(2)).chat(captor.capture());

        var firstReq = captor.getAllValues().get(0).messages();
        assertThat(firstReq).hasSize(2);

        var secondReq = captor.getAllValues().get(1).messages();
        assertThat(secondReq).hasSize(4);
        assertThat(secondReq.get(1).content()).isEqualTo("你好");            // 第一轮的问
        assertThat(secondReq.get(2).content()).isEqualTo("你好，我是助手");   // 第一轮的答
        assertThat(secondReq.get(3).content()).isEqualTo("我叫什么");         // 本轮的问
    }

    @Test
    @DisplayName("反例：拿别人的 sessionId（前缀不是自己的 userId）直接拒绝，且不触碰 LLM")
    void foreignSessionId_rejected() {
        assertThatThrownBy(() -> service.chat(admin(), new ChatWebRequest("hi", "999-abc")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话不存在");

        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("正例：服务重启后拿旧 sessionId 进来（归属正确但已不存在）→ 优雅重建继续对话")
    void unknownOwnedSession_rebuiltGracefully() {
        when(llmClient.chat(any())).thenReturn(resp("还在呢"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("hi", "1-ghost"));

        assertThat(out.sessionId()).isEqualTo("1-ghost");   // 保持原 id，客户端无感
        verify(llmClient).chat(any());
    }
}

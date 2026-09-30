package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.loop.AgentLoop;
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
 * AgentService 单元测试：Mockito 模拟 AgentLoop，重点验证
 * "第二轮传给循环的消息数组里真的带着第一轮的问答"——这就是多轮记忆的证明
 */
class AgentServiceTest {

    private AgentLoop agentLoop;
    private AgentProperties props;
    private ChatMemoryStore memoryStore;
    private AgentService service;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        memoryStore = new ChatMemoryStore(props);
        agentLoop = mock(AgentLoop.class);
        service = new AgentService(props, agentLoop, memoryStore);
    }

    private LoginUser admin() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("x");
        return new LoginUser(u, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private AgentLoop.LoopResult result(String reply) {
        return new AgentLoop.LoopResult(reply, List.of());
    }

    /**
     * 模拟 AgentLoop 的真实契约之一：把最终回答追加进传入的 messages 时间线。
     * mock 必须复现这个副作用，否则 service 存进记忆的就只有 user 消息，第二轮会"失忆"
     */
    private AgentLoop.LoopResult replyIntoTimeline(org.mockito.invocation.InvocationOnMock inv, String reply) {
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) inv.getArgument(0);
        msgs.add(ChatMessage.assistant(reply, null));
        return result(reply);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<ChatMessage>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    @Test
    @DisplayName("正例-首轮：sessionId 为空时创建会话，循环收到 system+user 两条消息")
    void firstRound_createsSession() {
        when(agentLoop.run(any(), any())).thenAnswer(inv -> replyIntoTimeline(inv, "你好，管理员"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("你好", null));

        assertThat(out.sessionId()).startsWith("1-");   // userId 前缀
        assertThat(out.toolTraces()).isEmpty();
        var captor = listCaptor();
        verify(agentLoop).run(captor.capture(), any());
        // captor 抓的是同一个列表引用，事后看到的是 mock 追加回答后的最终态：system + user + assistant
        var messages = captor.getValue();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(1).content()).isEqualTo("你好");
        assertThat(memoryStore.history(out.sessionId())).hasSize(2);   // 本轮问答已入库
    }

    @Test
    @DisplayName("正例-第二轮：传给循环的消息数组携带第一轮的 user+assistant，共 4 条")
    void secondRound_carriesHistory() {
        when(agentLoop.run(any(), any()))
                .thenAnswer(inv -> replyIntoTimeline(inv, "你好，我是助手"))
                .thenAnswer(inv -> replyIntoTimeline(inv, "你叫小明"));

        ChatWebResponse first = service.chat(admin(), new ChatWebRequest("你好", null));
        service.chat(admin(), new ChatWebRequest("我叫什么", first.sessionId()));

        var captor = listCaptor();
        verify(agentLoop, times(2)).run(captor.capture(), any());

        // 每次调用各自 new 列表，最终态：第一份 = system+user+assistant(3)，第二份 = 带全历史的 5 条
        assertThat(captor.getAllValues().get(0)).hasSize(3);

        var secondReq = captor.getAllValues().get(1);
        assertThat(secondReq).hasSize(5);
        assertThat(secondReq.get(1).content()).isEqualTo("你好");            // 第一轮的问
        assertThat(secondReq.get(2).content()).isEqualTo("你好，我是助手");   // 第一轮的答
        assertThat(secondReq.get(3).content()).isEqualTo("我叫什么");         // 本轮的问
        assertThat(secondReq.get(4).content()).isEqualTo("你叫小明");         // mock 追加的本轮答
    }

    @Test
    @DisplayName("反例：拿别人的 sessionId（前缀不是自己的 userId）直接拒绝，且不触碰循环")
    void foreignSessionId_rejected() {
        assertThatThrownBy(() -> service.chat(admin(), new ChatWebRequest("hi", "999-abc")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话不存在");

        verifyNoInteractions(agentLoop);
    }

    @Test
    @DisplayName("正例：服务重启后拿旧 sessionId 进来（归属正确但已不存在）→ 优雅重建继续对话")
    void unknownOwnedSession_rebuiltGracefully() {
        when(agentLoop.run(any(), any())).thenReturn(result("还在呢"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("hi", "1-ghost"));

        assertThat(out.sessionId()).isEqualTo("1-ghost");   // 保持原 id，客户端无感
        assertThat(memoryStore.exists("1-ghost")).isTrue(); // 重建后记忆恢复工作
        verify(agentLoop).run(any(), any());
    }
}

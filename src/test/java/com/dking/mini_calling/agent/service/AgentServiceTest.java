package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ToolCall;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.confirm.PendingActionStore;
import com.dking.mini_calling.agent.loop.AgentLoop;
import com.dking.mini_calling.agent.loop.PendingConfirmation;
import com.dking.mini_calling.agent.memory.ChatMemoryStore;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.agent.web.dto.ConfirmWebRequest;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.entity.SysUser;
import com.dking.mini_calling.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private PendingActionStore actionStore;
    private AgentService service;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        memoryStore = new ChatMemoryStore(props);
        agentLoop = mock(AgentLoop.class);
        actionStore = mock(PendingActionStore.class);
        service = new AgentService(props, agentLoop, memoryStore, actionStore);
    }

    private LoginUser admin() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("x");
        return new LoginUser(u, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private AgentLoop.LoopResult result(String reply) {
        return new AgentLoop.LoopResult(reply, List.of(), null);
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
        when(agentLoop.run(any(), any(), any())).thenAnswer(inv -> replyIntoTimeline(inv, "你好，管理员"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("你好", null));

        assertThat(out.sessionId()).startsWith("1-");   // userId 前缀
        assertThat(out.toolTraces()).isEmpty();
        var captor = listCaptor();
        verify(agentLoop).run(captor.capture(), any(), any());
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
        when(agentLoop.run(any(), any(), any()))
                .thenAnswer(inv -> replyIntoTimeline(inv, "你好，我是助手"))
                .thenAnswer(inv -> replyIntoTimeline(inv, "你叫小明"));

        ChatWebResponse first = service.chat(admin(), new ChatWebRequest("你好", null));
        service.chat(admin(), new ChatWebRequest("我叫什么", first.sessionId()));

        var captor = listCaptor();
        verify(agentLoop, times(2)).run(captor.capture(), any(), any());

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
        when(agentLoop.run(any(), any(), any())).thenAnswer(inv -> replyIntoTimeline(inv, "还在呢"));

        ChatWebResponse out = service.chat(admin(), new ChatWebRequest("hi", "1-ghost"));

        assertThat(out.sessionId()).isEqualTo("1-ghost");   // 保持原 id，客户端无感
        assertThat(memoryStore.exists("1-ghost")).isTrue(); // 重建后记忆恢复工作
        verify(agentLoop).run(any(), any(), any());
    }

    @Test
    @DisplayName("Phase 5 正例：approve → 真实执行工具、tool 消息回填快照后恢复循环")
    void confirm_approve_executesAndResumes() {
        // 生产真实形态：挂起发生在铁律①之后——快照含 assistant(tool_calls)，baseCount=1+空历史
        PendingActionStore.PendingAction action = new PendingActionStore.PendingAction(
                "pid-1", 1L, "1-s1",
                List.of(
                        ChatMessage.system("sys"),
                        ChatMessage.user("删除他"),
                        ChatMessage.assistant(null, List.of(new ToolCall(
                                "c1", "function", new ToolCall.Function("delete_user", "{}"))))),
                1, "c1", "delete_user", "{}",
                List.of(), Instant.now());
        when(actionStore.consume("pid-1", 1L)).thenReturn(action);
        // 模拟 AgentLoop.resume 的契约：执行被批准的工具（回填 tool 消息）+ 继续循环（追加最终回答）
        when(agentLoop.resume(any(), any(), any(), any())).thenAnswer(inv -> {
            List<ChatMessage> msgs = inv.getArgument(1);
            msgs.add(ChatMessage.tool("c1", "{\"deleted\":true}"));
            msgs.add(ChatMessage.assistant("已删除", null));
            return new AgentLoop.LoopResult("已删除", List.of(), null);
        });

        ChatWebResponse out = service.confirm(admin(), action, "approve", null);

        assertThat(out.reply()).isEqualTo("已删除");
        var captor = listCaptor();
        verify(agentLoop).resume(any(), captor.capture(), any(), any());
        var msgs = captor.getValue();
        assertThat(msgs).hasSize(5);   // 快照(3) + tool(执行结果) + assistant(最终回答，由 thenAnswer 追加)
        assertThat(msgs.get(3).role()).isEqualTo("tool");
        assertThat(msgs.get(3).toolCallId()).isEqualTo("c1");
        assertThat(msgs.get(4).content()).isEqualTo("已删除");
        assertThat(memoryStore.history("1-s1")).hasSize(4);   // baseCount=1 起截取，本轮增量入记忆
    }

    @Test
    @DisplayName("Phase 5 正例：reject → 工具不执行，以拒绝 error 回填让模型婉拒")
    void confirm_reject_rejected() {
        PendingActionStore.PendingAction action = new PendingActionStore.PendingAction(
                "pid-2", 1L, "1-s2",
                List.of(
                        ChatMessage.system("sys"),
                        ChatMessage.user("删除他"),
                        ChatMessage.assistant(null, List.of(new ToolCall(
                                "c1", "function", new ToolCall.Function("delete_user", "{}"))))),
                1, "c1", "delete_user", "{}",
                List.of(), Instant.now());
        when(actionStore.consume("pid-2", 1L)).thenReturn(action);
        // 模拟 AgentLoop.reject 的契约：拒绝消息 + 婉拒答复入时间线，工具不执行
        when(agentLoop.reject(any(), any(), any(), any())).thenAnswer(inv -> {
            List<ChatMessage> msgs = inv.getArgument(1);
            msgs.add(ChatMessage.tool("c1", AgentLoop.REJECTED_JSON));
            msgs.add(ChatMessage.assistant("好的，不删了", null));
            return new AgentLoop.LoopResult("好的，不删了", List.of(), null);
        });

        ChatWebResponse out = service.confirm(admin(), action, "reject", null);

        var captor = listCaptor();
        verify(agentLoop).reject(any(), captor.capture(), any(), any());
        var msgs = captor.getValue();
        assertThat(msgs.get(3).content()).contains("用户拒绝了");
        assertThat(memoryStore.history("1-s2")).hasSize(4);   // 拒绝的时间线同样入记忆
    }

    @Test
    @DisplayName("Phase 5 反例：decision 非法 → 明确报错（pendingId 失效的校验在 Controller 同步段）")
    void confirm_invalidDecision_throws() {
        PendingActionStore.PendingAction action = new PendingActionStore.PendingAction(
                "pid-3", 1L, "1-s3",
                List.of(ChatMessage.system("sys"), ChatMessage.user("删除他")),
                2, "c1", "delete_user", "{}",
                List.of(), Instant.now());

        assertThatThrownBy(() -> service.confirm(admin(), action, "bogus", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("approve");
    }

    @Test
    @DisplayName("Phase 5 边界：resume 返回新挂起（链式确认）→ 新动作入库并通知前端")
    void confirm_chainedPending_createsNextAction() {
        PendingActionStore.PendingAction action = new PendingActionStore.PendingAction(
                "pid-4", 1L, "1-s4",
                List.of(
                        ChatMessage.system("sys"),
                        ChatMessage.user("删两个"),
                        ChatMessage.assistant(null, List.of(new ToolCall(
                                "c1", "function", new ToolCall.Function("delete_user", "{}"))))),
                1, "c1", "delete_user", "{}",
                List.of(), Instant.now());
        when(actionStore.consume("pid-4", 1L)).thenReturn(action);
        // resume 后又出现新的危险工具调用 → 返回挂起结果
        when(agentLoop.resume(any(), any(), any(), any())).thenReturn(new AgentLoop.LoopResult(
                null, List.of(), new PendingConfirmation(
                        List.of(ChatMessage.user("删两个")),
                        "c2", "delete_user", "{\"nickname\":\"x\"}", List.of())));
        when(actionStore.create(any(), any(), any(), eq(1), eq("c2"), eq("delete_user"), eq("{\"nickname\":\"x\"}"), any()))
                .thenReturn(new PendingActionStore.PendingAction(
                        "pid-5", 1L, "1-s4", List.of(), 1,
                        "c2", "delete_user", "{}", List.of(), Instant.now()));

        ChatWebResponse out = service.confirm(admin(), action, "approve", null);

        // 新挂起入库并作为 Confirmation 返回
        assertThat(out.confirmation()).isNotNull();
        assertThat(out.confirmation().pendingId()).isEqualTo("pid-5");
        assertThat(out.confirmation().toolName()).isEqualTo("delete_user");
        verify(actionStore).create(eq(1L), eq("1-s4"), any(), eq(1), eq("c2"),
                eq("delete_user"), eq("{\"nickname\":\"x\"}"), any());
    }
}

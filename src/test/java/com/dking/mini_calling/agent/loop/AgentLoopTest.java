package com.dking.mini_calling.agent.loop;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.StreamCollector;
import com.dking.mini_calling.agent.client.dto.ChatChunk;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.client.dto.ToolCall;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.tool.AgentTool;
import com.dking.mini_calling.agent.tool.CurrentTimeTool;
import com.dking.mini_calling.agent.tool.EchoTool;
import com.dking.mini_calling.agent.tool.ToolContext;
import com.dking.mini_calling.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopTest {

    private LlmClient llmClient;
    private AgentProperties props;
    private ToolRegistry registry;
    private AgentLoop loop;
    private final ToolContext ctx = new ToolContext(null);

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        llmClient = mock(LlmClient.class);
        registry = new ToolRegistry(List.of(new CurrentTimeTool(), new EchoTool()));
        loop = new AgentLoop(props, llmClient, registry);
    }

    // ─── 测试辅助 ───

    private ChatResponse textResp(String content, String finish) {
        return new ChatResponse("id", "glm-4.6",
                List.of(new ChatResponse.Choice(new ChatMessage("assistant", content, null, null), finish)),
                new ChatResponse.Usage(1, 1, 2));
    }

    private ChatResponse toolCallResp(ToolCall... calls) {
        return new ChatResponse("id", "glm-4.6",
                List.of(new ChatResponse.Choice(new ChatMessage("assistant", null, List.of(calls), null), "tool_calls")),
                new ChatResponse.Usage(1, 1, 2));
    }

    private ToolCall call(String id, String name, String argsJson) {
        return new ToolCall(id, "function", new ToolCall.Function(name, argsJson));
    }

    private List<ChatMessage> runOnce(String userText) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(userText));
        loop.run(messages, ctx);
        return messages;
    }

    // ─── 流式测试辅助 ───

    private ChatChunk chunkText(String text) {
        return new ChatChunk(List.of(new ChatChunk.Choice(new ChatChunk.Delta(text, null), null)), null);
    }

    private ChatChunk chunkTool(String id, String name) {
        return new ChatChunk(List.of(new ChatChunk.Choice(new ChatChunk.Delta(null,
                List.of(new ChatChunk.DeltaToolCall(0, id, "function",
                        new ChatChunk.DeltaToolCall.Function(name, "{}")))), null)), null);
    }

    @Test
    @DisplayName("正例：三跳链路——模型点名工具→执行回填→给出最终回答，三条铁律全部落实")
    void threeHop_toolLoop() {
        when(llmClient.chat(any())).thenReturn(
                toolCallResp(call("c1", "get_current_time", "{}")),
                textResp("现在是 12:00", "stop"));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("几点了"))), ctx);

        assertThat(result.reply()).isEqualTo("现在是 12:00");
        assertThat(result.traces()).hasSize(1);
        assertThat(result.traces().get(0).tool()).isEqualTo("get_current_time");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient, times(2)).chat(captor.capture());

        // captor 抓到的是同一个可变列表的引用：断言时它已含最终回答（循环结束前循环自己追加了 assistant）
        List<ChatMessage> second = captor.getAllValues().get(1).messages();
        assertThat(second).hasSize(4);
        assertThat(second.get(1).toolCalls()).isNotEmpty();                  // 铁律①：assistant 原样入栈
        assertThat(second.get(2).role()).isEqualTo("tool");                  // 铁律③：tool 消息回填
        assertThat(second.get(2).toolCallId()).isEqualTo("c1");
        assertThat(second.get(2).content()).contains("time");                // 真实执行了时间工具
        assertThat(second.get(3).content()).isEqualTo("现在是 12:00");        // 最终回答也写进了时间线
    }

    @Test
    @DisplayName("正例：一条 assistant 消息带 2 个 tool_calls → 产生 2 条 tool 回应，id 一一对应")
    void twoToolCalls_inOneMessage() {
        when(llmClient.chat(any())).thenReturn(
                toolCallResp(
                        call("c1", "echo", "{\"text\":\"甲\"}"),
                        call("c2", "echo", "{\"text\":\"乙\"}")),
                textResp("两件事都办完了", "stop"));

        List<ChatMessage> messages = runOnce("把甲和乙各回显一次");

        assertThat(messages).hasSize(5);
        assertThat(messages.get(2).toolCallId()).isEqualTo("c1");
        assertThat(messages.get(2).content()).contains("甲");
        assertThat(messages.get(3).toolCallId()).isEqualTo("c2");
        assertThat(messages.get(3).content()).contains("乙");
        assertThat(messages.get(4).content()).isEqualTo("两件事都办完了");   // 最终回答也被追加入时间线
    }

    private static final class BoomTool implements com.dking.mini_calling.agent.tool.AgentTool {
        @Override public String name() { return "boom"; }
        @Override public String description() { return "总是失败的工具"; }
        @Override public java.util.Map<String, Object> parametersSchema() {
            return java.util.Map.of("type", "object", "properties", java.util.Map.of());
        }
        @Override public String requiredPermission() { return ""; }
        @Override public String execute(com.fasterxml.jackson.databind.JsonNode args, ToolContext ctx) {
            throw new IllegalStateException("数据库炸了");
        }
    }

    @Test
    @DisplayName("正例（铁律②）：工具抛异常 → 错误 JSON 回传模型，循环自愈而不是崩溃")
    void toolException_becomesErrorJson() {
        registry = new ToolRegistry(List.of(new BoomTool()));
        loop = new AgentLoop(props, llmClient, registry);
        when(llmClient.chat(any())).thenReturn(
                toolCallResp(call("c1", "boom", "{}")),
                textResp("工具出了点问题，抱歉", "stop"));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("触发boom"))), ctx);

        assertThat(result.reply()).isEqualTo("工具出了点问题，抱歉");
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient, times(2)).chat(captor.capture());
        List<ChatMessage> second = captor.getAllValues().get(1).messages();
        assertThat(second.get(2).content()).contains("error").contains("数据库炸了");
    }

    @Test
    @DisplayName("反例：模型点名不存在的工具 → 错误 JSON 回传，循环继续到最终回答")
    void unknownTool_becomesErrorJson() {
        when(llmClient.chat(any())).thenReturn(
                toolCallResp(call("c1", "no_such_tool", "{}")),
                textResp("没有这个能力", "stop"));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("乱调一个"))), ctx);

        assertThat(result.reply()).isEqualTo("没有这个能力");
        assertThat(result.traces().get(0).resultSummary()).contains("未知工具");
    }

    @Test
    @DisplayName("反例：模型传的参数不是合法 JSON → 错误 JSON 回传，循环继续")
    void illegalArguments_becomeErrorJson() {
        when(llmClient.chat(any())).thenReturn(
                toolCallResp(call("c1", "echo", "{这不是json")),
                textResp("参数错了，我重试也不行就算了", "stop"));

        List<ChatMessage> messages = runOnce("echo 一个坏参数");

        assertThat(messages.get(2).content()).contains("JSON");
    }

    @Test
    @DisplayName("正例：模型连续点名工具不停手 → 达到 maxRounds 强制止血")
    void maxRounds_forcesStop() {
        props.setMaxRounds(2);
        when(llmClient.chat(any())).thenReturn(toolCallResp(call("c1", "get_current_time", "{}")));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("无限循环试试"))), ctx);

        assertThat(result.reply()).contains("最大工具调用轮数");
        verify(llmClient, times(2)).chat(any());
        assertThat(result.traces()).hasSize(2);
    }

    @Test
    @DisplayName("正例：finish_reason=length（回答被截断）→ 返回友好提示而不是半截话")
    void lengthFinish_getsFriendlyHint() {
        when(llmClient.chat(any())).thenReturn(textResp("这是被掐断的一半", "length"));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("讲个长故事"))), ctx);

        assertThat(result.reply()).contains("截断");
    }

    @Test
    @DisplayName("正例（流式）：chatStream 分片推送 → sink 收到 delta 事件，聚合回答与非流式一致")
    void streamingLoop_emitsDeltaEvents() {
        doAnswer(inv -> {
            Consumer<ChatChunk> onChunk = inv.getArgument(1);
            onChunk.accept(chunkText("你"));
            onChunk.accept(chunkText("好"));
            return null;
        }).when(llmClient).chatStream(any(), any());
        when(llmClient.chat(any())).thenReturn(textResp("不应被调用", "stop"));   // 防呆

        List<AgentEvent> events = new ArrayList<>();
        AgentLoop.LoopResult result = loop.run(
                new ArrayList<>(List.of(ChatMessage.user("你好"))), ctx, events::add);

        assertThat(result.reply()).isEqualTo("你好");
        assertThat(events).extracting(AgentEvent::type).containsExactly("delta", "delta");
        verify(llmClient, times(1)).chatStream(any(), any());
        verify(llmClient, never()).chat(any());
    }

    @Test
    @DisplayName("正例（流式）：工具链路 → sink 依次收到 tool、tool_result、delta 事件")
    void streamingLoop_toolEvents() {
        doAnswer(inv -> {
            Consumer<ChatChunk> onChunk = inv.getArgument(1);
            onChunk.accept(chunkTool("c1", "get_current_time"));
            return null;
        }).doAnswer(inv -> {
            Consumer<ChatChunk> onChunk = inv.getArgument(1);
            onChunk.accept(chunkText("现在是 12:00"));
            return null;
        }).when(llmClient).chatStream(any(), any());

        List<AgentEvent> events = new ArrayList<>();
        AgentLoop.LoopResult result = loop.run(
                new ArrayList<>(List.of(ChatMessage.user("几点了"))), ctx, events::add);

        assertThat(result.reply()).isEqualTo("现在是 12:00");
        assertThat(events).extracting(AgentEvent::type).containsExactly("tool", "tool_result", "delta");
    }

    @Test
    @DisplayName("Phase 5：needConfirm 工具 → 循环挂起返回 pending，工具未执行，快照完整")
    void needConfirm_suspendsLoop() {
        AgentTool confirmTool = mock(AgentTool.class);
        when(confirmTool.name()).thenReturn("dangerous_thing");
        when(confirmTool.description()).thenReturn("危险操作");
        when(confirmTool.parametersSchema()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(confirmTool.requiredPermission()).thenReturn("");
        when(confirmTool.needConfirm()).thenReturn(true);
        registry = new ToolRegistry(List.of(confirmTool));
        loop = new AgentLoop(props, llmClient, registry);

        when(llmClient.chat(any())).thenReturn(toolCallResp(call("c1", "dangerous_thing", "{}")));

        AgentLoop.LoopResult result = loop.run(new ArrayList<>(List.of(ChatMessage.user("删了他"))), ctx);

        assertThat(result.pending()).isNotNull();
        assertThat(result.reply()).isNull();
        assertThat(result.pending().callId()).isEqualTo("c1");
        assertThat(result.pending().toolName()).isEqualTo("dangerous_thing");
        assertThat(result.pending().toolArguments()).isEqualTo("{}");
        assertThat(result.pending().remainingCalls()).isEmpty();
        assertThat(result.pending().messagesSnapshot()).hasSize(2);
        assertThat(result.pending().messagesSnapshot().get(0).role()).isEqualTo("user");
        assertThat(result.pending().messagesSnapshot().get(1).toolCalls()).isNotEmpty();   // 铁律①快照
        verify(llmClient, times(1)).chat(any());   // 只有一跳：挂起后不再调 LLM
    }

    @Test
    @DisplayName("Phase 5：approve 恢复——工具结果以 tool 消息回填快照，run() 复用生成最终回答")
    void resumeAfterConfirm_reusesRun() {
        AgentTool confirmTool = mock(AgentTool.class);
        when(confirmTool.name()).thenReturn("dangerous_thing");
        when(confirmTool.description()).thenReturn("危险操作");
        when(confirmTool.parametersSchema()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(confirmTool.requiredPermission()).thenReturn("");
        when(confirmTool.needConfirm()).thenReturn(true);
        registry = new ToolRegistry(List.of(confirmTool));
        loop = new AgentLoop(props, llmClient, registry);

        when(llmClient.chat(any())).thenReturn(
                toolCallResp(call("c1", "dangerous_thing", "{}")),
                textResp("执行完成", "stop"));

        AgentLoop.LoopResult first = loop.run(new ArrayList<>(List.of(ChatMessage.user("做事"))), ctx);
        assertThat(first.pending()).isNotNull();

        // 模拟 AgentService.confirm(approve) 的恢复动作：快照 + 工具结果 → 再调 run()
        List<ChatMessage> resumed = new ArrayList<>(first.pending().messagesSnapshot());
        resumed.add(ChatMessage.tool(first.pending().callId(), "{\"done\":true}"));
        AgentLoop.LoopResult finalResult = loop.run(resumed, ctx);

        assertThat(finalResult.reply()).isEqualTo("执行完成");
        assertThat(finalResult.pending()).isNull();
        assertThat(resumed).hasSize(4);   // user + assistant(tc) + tool + assistant(最终)
    }
}
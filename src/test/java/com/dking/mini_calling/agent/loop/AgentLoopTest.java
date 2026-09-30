package com.dking.mini_calling.agent.loop;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.client.dto.ToolCall;
import com.dking.mini_calling.agent.config.AgentProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
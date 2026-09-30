package com.dking.mini_calling.agent.loop;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.client.dto.ToolCall;
import com.dking.mini_calling.agent.client.dto.ToolDefinition;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.tool.ToolContext;
import com.dking.mini_calling.agent.tool.ToolRegistry;
import com.dking.mini_calling.agent.web.dto.ToolTrace;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写的 function calling 循环——整个 agent 模块的心脏（目标 ≤80 行，每一行都值得读懂）。
 *
 * 控制流：发消息 → 模型要么给最终回答（结束），要么点名调工具 → 执行 → 结果回填 → 再发 → 循环。
 * 三条铁律（每条背后都是一次真实的线上故障）：
 *   ① 含 tool_calls 的 assistant 消息必须先原样入栈，再执行工具——协议要求每个 tool 消息
 *     都能找到它回应的那次 tool_call；
 *   ② 工具失败以 {"error":...} 回传模型而不是抛异常（落在 ToolRegistry 里）——模型可以
 *     换参数重试或向用户解释，只有基础设施故障才中断；
 *   ③ tool 消息必须携带原始 tool_call_id，多条工具调用时一一对应不能错位。
 */
@Component
public class AgentLoop {

    private final AgentProperties props;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public AgentLoop(AgentProperties props, LlmClient llmClient, ToolRegistry toolRegistry) {
        this.props = props;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 跑完一整轮对话。会原地把本轮新增的 assistant/tool 消息追加进传入的 messages 列表
     * （调用方据此把完整时间线写入会话记忆）
     */
    public LoopResult run(List<ChatMessage> messages, ToolContext ctx) {
        List<ToolTrace> traces = new ArrayList<>();
        List<ToolDefinition> tools = toolRegistry.definitions();

        for (int round = 1; round <= props.getMaxRounds(); round++) {
            ChatResponse resp = llmClient.chat(ChatRequest.of(props, messages, tools));
            ChatMessage assistant = resp.choices().get(0).message();

            // 铁律①
            messages.add(assistant);

            if (assistant.toolCalls() == null || assistant.toolCalls().isEmpty()) {
                if ("length".equalsIgnoreCase(resp.choices().get(0).finishReason())) {
                    return new LoopResult("回答因长度限制被截断，请把问题拆小一点再问", traces);
                }
                String content = assistant.content() == null ? "（模型没有返回内容）" : assistant.content();
                return new LoopResult(content, traces);
            }

            // 模型一次可能点名多个工具（"并行" tool_calls），逐个串行执行即可
            for (int i = 0; i < assistant.toolCalls().size(); i++) {
                ToolCall call = assistant.toolCalls().get(i);
                // 智谱的 tool_call.id 偶发为空：本地补占位，保持"每个 tool 消息都有 id"的协议闭合
                String callId = StringUtils.hasText(call.id()) ? call.id() : "call_" + round + "_" + i;
                String resultJson = toolRegistry.invoke(call.function().name(), call.function().arguments(), ctx);
                traces.add(new ToolTrace(call.function().name(), call.function().arguments(), summarize(resultJson)));
                // 铁律③
                messages.add(ChatMessage.tool(callId, resultJson));
            }
        }
        // 终止兜底：模型反复点名工具不停手时，强制止血
        return new LoopResult("已达最大工具调用轮数（" + props.getMaxRounds() + "），请把问题拆小一点再问", traces);
    }

    /** 喂给前端的轨迹只留摘要，工具返回再大也不撑爆响应体 */
    private String summarize(String json) {
        return json.length() <= 200 ? json : json.substring(0, 200) + "…";
    }

    public record LoopResult(String reply, List<ToolTrace> traces) {
    }
}

package com.dking.mini_calling.agent.loop;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.StreamCollector;
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
import java.util.function.Consumer;

/**
 * 手写的 function calling 循环——整个 agent 模块的心脏。
 *
 * 控制流：发消息 → 模型要么给最终回答（结束），要么点名调工具 → 过确认闸 → 执行 → 结果回填 → 再发 → 循环。
 * 三条铁律（每条背后都是一次真实的线上故障）：
 *   ① 含 tool_calls 的 assistant 消息必须先原样入栈，再执行工具；
 *   ② 工具失败以 {"error":...} 回传模型而不是抛异常（落在 ToolRegistry 里）；
 *   ③ tool 消息必须携带原始 tool_call_id。
 *
 * Phase 5 重构后的三个"唯一出口"（解耦的核心）：
 *   - executeTool：全项目唯一的工具执行点（tool 事件 → invoke → tool_result 事件）；
 *   - runToolCalls：全项目唯一的确认闸（needConfirm 命中即挂起），首轮/恢复共用；
 *   - sinkOrSilent：空订阅者的 Null Object，调用处不再出现 if (sink != null)。
 * 所有公开入口（run/resume/reject）最终都收敛到 runLoop。
 */
@Component
public class AgentLoop {

    /** Null Object 模式：没有事件订阅者时用它，调用处不再判空 */
    private static final Consumer<AgentEvent> SILENT = event -> { };

    /** 拒绝也以 role=tool 消息回传模型（协议一致性），模型据此生成婉拒答复 */
    public static final String REJECTED_JSON = "{\"error\":\"用户拒绝了这个操作，请勿再次尝试，向用户说明即可\"}";

    private final AgentProperties props;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;

    public AgentLoop(AgentProperties props, LlmClient llmClient, ToolRegistry toolRegistry) {
        this.props = props;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    // ─── 公开入口 ───

    public LoopResult run(List<ChatMessage> messages, ToolContext ctx) {
        return run(messages, ctx, null);
    }

    /** sink 非 null 时走流式 LLM 调用并把过程实时外送；null 时与非流式行为一致 */
    public LoopResult run(List<ChatMessage> messages, ToolContext ctx, Consumer<AgentEvent> sink) {
        Consumer<AgentEvent> out = sinkOrSilent(sink);
        return runLoop(messages, ctx, out, sink != null, new ArrayList<>());
    }

    /**
     * Phase 5：从挂起点恢复。被批准的工具直接执行（不再过确认闸），
     * 其余工具照常过闸（还危险就再次挂起，链式确认），随后继续 LLM 轮次。
     * messages 是可变工作数组：方法原地追加，调用方据此写会话记忆（与 run 相同的契约）
     */
    public LoopResult resume(PendingConfirmation pending, List<ChatMessage> messages,
                             ToolContext ctx, Consumer<AgentEvent> sink) {
        Consumer<AgentEvent> out = sinkOrSilent(sink);
        List<ToolTrace> traces = new ArrayList<>();

        ToolCall approved = new ToolCall(pending.callId(), "function",
                new ToolCall.Function(pending.toolName(), pending.toolArguments()));
        String resultJson = executeTool(approved, ctx, out, traces);
        messages.add(ChatMessage.tool(pending.callId(), resultJson));

        PendingConfirmation next = runToolCalls(pending.remainingCalls(), 0, messages, ctx, out, traces);
        if (next != null) {
            return new LoopResult(null, traces, next);   // 剩余里还有危险工具：链式确认
        }
        return runLoop(messages, ctx, out, sink != null, traces);
    }

    /**
     * 拒绝：只拒绝被挂起的这一个工具（以"用户拒绝"回填）；同批剩余的调用继续过确认闸，
     * 由用户逐个独立裁决——拒绝一个绝不等于放弃全部（Phase 5.2 实测反馈修正）
     */
    public LoopResult reject(PendingConfirmation pending, List<ChatMessage> messages,
                             ToolContext ctx, Consumer<AgentEvent> sink) {
        Consumer<AgentEvent> out = sinkOrSilent(sink);
        List<ToolTrace> traces = new ArrayList<>();
        messages.add(ChatMessage.tool(pending.callId(), REJECTED_JSON));
        PendingConfirmation next = runToolCalls(pending.remainingCalls(), 0, messages, ctx, out, traces);
        if (next != null) {
            return new LoopResult(null, traces, next);   // 剩余里还有危险工具：继续弹确认卡片
        }
        return runLoop(messages, ctx, out, sink != null, traces);
    }

    // ─── 循环主体 ───

    private LoopResult runLoop(List<ChatMessage> messages, ToolContext ctx,
                               Consumer<AgentEvent> out, boolean streaming, List<ToolTrace> traces) {
        List<ToolDefinition> tools = toolRegistry.definitions();

        for (int round = 1; round <= props.getMaxRounds(); round++) {
            ChatResponse resp = streaming
                    ? streamLlm(ChatRequest.streaming(props, messages, tools), out)
                    : llmClient.chat(ChatRequest.of(props, messages, tools));
            // 到这里，流式/非流式都变成了 resp，没有区别
            ChatMessage assistant = resp.choices().get(0).message();

            // 铁律①
            messages.add(assistant);

            if (assistant.toolCalls() == null || assistant.toolCalls().isEmpty()) {
                if ("length".equalsIgnoreCase(resp.choices().get(0).finishReason())) {
                    return new LoopResult("回答因长度限制被截断，请把问题拆小一点再问", traces, null);
                }
                return new LoopResult(contentOrFallback(assistant), traces, null);
            }

            // 确认闸统一在咽喉里：needConfirm 命中 → 挂起整个循环等人工裁决
            PendingConfirmation pending = runToolCalls(assistant.toolCalls(), 0, messages, ctx, out, traces);
            if (pending != null) {
                return new LoopResult(null, traces, pending);
            }
        }
        // 终止兜底：模型反复点名工具不停手时，强制止血
        return new LoopResult("已达最大工具调用轮数（" + props.getMaxRounds() + "），请把问题拆小一点再问", traces, null);
    }

    // ─── 三个唯一出口 ───

    /**
     * 确认闸 + 执行的统一咽喉：清单里每个工具都必须经过这里。
     * needConfirm 命中 → 以当前 messages 为快照挂起（已执行部分天然在快照里），返回挂起意图
     */
    private PendingConfirmation runToolCalls(List<ToolCall> calls, int fromIndex,
                                             List<ChatMessage> messages, ToolContext ctx,
                                             Consumer<AgentEvent> out, List<ToolTrace> traces) {
        for (int i = fromIndex; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            // 智谱的 tool_call.id 偶发为空：本地补占位，保持"每个 tool 消息都有 id"的协议闭合（铁律③）
            String callId = StringUtils.hasText(call.id()) ? call.id() : "call_" + i;

            if (toolRegistry.needConfirm(call.function().name())) {
                return new PendingConfirmation(
                        List.copyOf(messages), callId,
                        call.function().name(), call.function().arguments(),
                        List.copyOf(calls.subList(i + 1, calls.size())));
            }
            String resultJson = executeTool(call, ctx, out, traces);
            messages.add(ChatMessage.tool(callId, resultJson));
        }
        return null;
    }

    /** 全项目唯一的工具执行点：tool 事件 → invoke → tool_result 事件 → 轨迹 */
    private String executeTool(ToolCall call, ToolContext ctx, Consumer<AgentEvent> out, List<ToolTrace> traces) {
        out.accept(AgentEvent.tool(call.function().name(), call.function().arguments()));
        String resultJson = toolRegistry.invoke(call.function().name(), call.function().arguments(), ctx);
        if (resultJson == null) {
            resultJson = "{\"error\":\"工具没有返回内容\"}";   // 工具返回 null 也按协议回传，保住时间线完整
        }
        traces.add(new ToolTrace(call.function().name(), call.function().arguments(), summarize(resultJson)));
        out.accept(AgentEvent.toolResult(call.function().name(), summarize(resultJson)));
        return resultJson;
    }

    private ChatResponse streamLlm(ChatRequest request, Consumer<AgentEvent> out) {
        StreamCollector collector = new StreamCollector();
        llmClient.chatStream(request, chunk -> {
            collector.accept(chunk);
            String text = chunk.deltaText();
            if (StringUtils.hasText(text)) {
                out.accept(AgentEvent.delta(text));
            }
        });
        return collector.toResponse();
    }

    private Consumer<AgentEvent> sinkOrSilent(Consumer<AgentEvent> sink) {
        return sink == null ? SILENT : sink;
    }

    /** 喂给前端的轨迹只留摘要，工具返回再大也不撑爆响应体 */
    private String summarize(String json) {
        return json.length() <= 200 ? json : json.substring(0, 200) + "…";
    }

    private String contentOrFallback(ChatMessage assistant) {
        return assistant.content() == null ? "（模型没有返回内容）" : assistant.content();
    }

    /** pending 非 null = 循环因等待人工确认而挂起，reply 为 null；恢复时以快照重新进入 runLoop */
    public record LoopResult(String reply, List<ToolTrace> traces, PendingConfirmation pending) {
    }
}

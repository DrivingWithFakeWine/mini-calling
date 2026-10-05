package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.client.dto.ToolCall;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.confirm.PendingActionStore;
import com.dking.mini_calling.agent.loop.AgentEvent;
import com.dking.mini_calling.agent.loop.AgentLoop;
import com.dking.mini_calling.agent.memory.ChatMemoryStore;
import com.dking.mini_calling.agent.tool.ToolContext;
import com.dking.mini_calling.agent.tool.ToolRegistry;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.agent.web.dto.ConfirmWebRequest;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * agent 编排门面：会话定位 → 消息数组组装 → 交给 AgentLoop（含工具循环）→ 时间线写回记忆。
 * Phase 5 起增加确认编排：危险操作挂起入库，人工裁决后从快照恢复执行。
 */
@Service
public class AgentService {

    /** 系统提示词：给模型设定角色 + 工具使用纪律（角色确认纪律是实测"瞎猜角色 id"后补的） */
    private static final String SYSTEM_PROMPT =
            "你是 mini_calling 系统的管理助手，帮助管理员查询和管理系统的用户与角色。"
                    + "需要查询或操作系统数据时，优先调用提供的工具，不要凭空编造数据；"
                    + "提到角色/权限时一律使用名称，id 由你调用查询工具获得，绝不猜测；"
                    + "创建用户时如果对方没有说明要分配什么角色，先展示角色列表并询问，确认后再创建（明确说不需要角色才可跳过）；"
                    + "请用简洁的中文回答。";

    /** 拒绝也以 role=tool 消息回传模型（协议一致性），模型据此生成婉拒答复 */
    private static final String REJECTED_JSON = "{\"error\":\"用户拒绝了这个操作，请勿再次尝试，向用户说明即可\"}";

    private final AgentProperties agentProperties;
    private final AgentLoop agentLoop;
    private final ChatMemoryStore memoryStore;
    private final ToolRegistry toolRegistry;
    private final PendingActionStore actionStore;

    public AgentService(AgentProperties agentProperties, AgentLoop agentLoop, ChatMemoryStore memoryStore,
                        ToolRegistry toolRegistry, PendingActionStore actionStore) {
        this.agentProperties = agentProperties;
        this.agentLoop = agentLoop;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
        this.actionStore = actionStore;
    }

    public ChatWebResponse chat(LoginUser user, ChatWebRequest request) {
        return doChat(user, request, null);
    }

    /** Phase 4：同一条链路，把过程事件实时交给 sink（由 Controller 转成 SSE 推给前端） */
    public void chatStream(LoginUser user, ChatWebRequest request, Consumer<AgentEvent> sink) {
        doChat(user, request, sink);
    }

    private ChatWebResponse doChat(LoginUser user, ChatWebRequest request, Consumer<AgentEvent> sink) {
        String sessionId = resolveOrCreate(user, request.sessionId());
        if (sink != null) {
            sink.accept(AgentEvent.start(sessionId));   // 首个事件先给 sessionId：即使流中断，前端也有归属
        }

        // 模型无状态：每次请求都要重发完整数组 = system 人设 + 该会话历史 + 本轮问题
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.addAll(memoryStore.history(sessionId));

        int beforeTurn = messages.size();   // 此下标之后全是本轮新增（user → 工具链 → 最终回答）

        messages.add(ChatMessage.user(request.message()));

        AgentLoop.LoopResult result = agentLoop.run(messages, new ToolContext(user), sink);

        // Phase 5：危险操作挂起——流程未完成，本轮不写记忆；等人工裁决后从快照恢复
        if (result.pending() != null) {
            PendingActionStore.PendingAction action = actionStore.create(user.getUserId(), sessionId,
                    result.pending().messagesSnapshot(), beforeTurn,
                    result.pending().callId(), result.pending().toolName(),
                    result.pending().toolArguments(), result.pending().remainingCalls());
            if (sink != null) {
                sink.accept(AgentEvent.confirmRequired(action.pendingId(), action.toolName(), action.toolArguments()));
            }
            return new ChatWebResponse("【待确认】工具 " + action.toolName() + " 等待人工确认",
                    sessionId, result.traces(),
                    new ChatWebResponse.Confirmation(action.pendingId(), action.toolName(), action.toolArguments()));
        }

        // 本轮完整时间线成段入库（含 assistant(tool_calls) 和 tool 消息），下一轮才能带着工具上下文
        memoryStore.append(sessionId,
                messages.subList(beforeTurn, messages.size()).toArray(new ChatMessage[0]));

        return new ChatWebResponse(result.reply(), sessionId, result.traces(), null);
    }

    /**
     * Phase 5：人工裁决后恢复挂起的循环。
     * approve = 真实执行被挂起的工具（含本轮剩余工具），继续循环生成最终回答；
     * reject  = 以 {"error":"用户拒绝"} 作为 tool 消息回填（协议一致性），模型基于它生成婉拒答复。
     * 两者都复用 run()：恢复点之后循环照常走，无需任何"续跑"专用逻辑
     */
    public ChatWebResponse confirm(LoginUser user, PendingActionStore.PendingAction action,
                                   String decision, Consumer<AgentEvent> sink) {
        boolean approve = "approve".equalsIgnoreCase(decision);
        if (!approve && !"reject".equalsIgnoreCase(decision)) {
            throw new BusinessException("decision 必须是 approve 或 reject");
        }
        if (sink != null) {
            sink.accept(AgentEvent.start(action.sessionId()));
        }
        // 若挂起期间服务重启、会话已不在：原地重建，防止确认执行的时间线静默丢失
        memoryStore.ensure(action.sessionId());

        List<ChatMessage> messages = new ArrayList<>(action.messagesSnapshot());
        if (approve) {
            // 事件次序与 AgentLoop 一致：tool 事件先于执行（前端先看到"执行中…"），结果后到
            if (sink != null) {
                sink.accept(AgentEvent.tool(action.toolName(), action.toolArguments()));
            }
            String resultJson = toolRegistry.invoke(action.toolName(), action.toolArguments(), new ToolContext(user));
            if (sink != null) {
                sink.accept(AgentEvent.toolResult(action.toolName(), resultJson));
            }
            messages.add(ChatMessage.tool(action.callId(), resultJson));

            // 同一条 assistant 消息里的其余并行调用逐一执行；needConfirm 的同样先挂起——
            // 已执行的部分安全落在 messages 里作为新快照，绝不能因为"用户批了第一个"就放行全部
            for (int i = 0; i < action.remainingCalls().size(); i++) {
                ToolCall rest = action.remainingCalls().get(i);
                if (toolRegistry.needConfirm(rest.function().name())) {
                    String restCallId = StringUtils.hasText(rest.id()) ? rest.id() : "call_confirm_" + i;
                    PendingActionStore.PendingAction next = actionStore.create(user.getUserId(),
                            action.sessionId(), messages, action.baseCount(), restCallId,
                            rest.function().name(), rest.function().arguments(),
                            action.remainingCalls().subList(i + 1, action.remainingCalls().size()));
                    if (sink != null) {
                        sink.accept(AgentEvent.confirmRequired(next.pendingId(), next.toolName(), next.toolArguments()));
                    }
                    return new ChatWebResponse("【待确认】工具 " + next.toolName() + " 等待人工确认",
                            action.sessionId(), List.of(),
                            new ChatWebResponse.Confirmation(next.pendingId(), next.toolName(), next.toolArguments()));
                }
                if (sink != null) {
                    sink.accept(AgentEvent.tool(rest.function().name(), rest.function().arguments()));
                }
                String r = toolRegistry.invoke(rest.function().name(), rest.function().arguments(), new ToolContext(user));
                if (sink != null) {
                    sink.accept(AgentEvent.toolResult(rest.function().name(), r));
                }
                messages.add(ChatMessage.tool(rest.id(), r));
            }
        } else {
            messages.add(ChatMessage.tool(action.callId(), REJECTED_JSON));
            for (ToolCall rest : action.remainingCalls()) {
                messages.add(ChatMessage.tool(rest.id(), REJECTED_JSON));
            }
        }

        AgentLoop.LoopResult result = agentLoop.run(messages, new ToolContext(user), sink);

        // 恢复后的完整时间线从 baseCount（system+历史的分界）开始截取入库，不重不漏
        memoryStore.append(action.sessionId(),
                messages.subList(action.baseCount(), messages.size()).toArray(new ChatMessage[0]));

        // 确认后模型又点名了另一个危险工具 → 再挂起，前端再次弹确认卡片（链式确认）
        if (result.pending() != null) {
            PendingActionStore.PendingAction next = actionStore.create(user.getUserId(), action.sessionId(),
                    result.pending().messagesSnapshot(), action.baseCount(),
                    result.pending().callId(), result.pending().toolName(),
                    result.pending().toolArguments(), result.pending().remainingCalls());
            if (sink != null) {
                sink.accept(AgentEvent.confirmRequired(next.pendingId(), next.toolName(), next.toolArguments()));
            }
        }

        return new ChatWebResponse(result.reply(), action.sessionId(), result.traces(), null);
    }

    /**
     * 会话归属校验：sessionId 必须以自己的 userId 开头。
     * 没有这行，拿到别人 sessionId 的请求就能读走别人的对话历史。
     */
    private String resolveOrCreate(LoginUser user, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return memoryStore.create(user.getUserId());
        }
        if (!sessionId.startsWith(user.getUserId() + "-")) {
            throw new BusinessException("会话不存在或已过期");
        }
        if (!memoryStore.exists(sessionId)) {
            memoryStore.ensure(sessionId);   // 服务重启/过期后拿旧 id 进来：原地重建，客户端无感
        }
        return sessionId;
    }
}

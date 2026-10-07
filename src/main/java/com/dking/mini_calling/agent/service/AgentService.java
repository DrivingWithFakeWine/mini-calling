package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.confirm.PendingActionStore;
import com.dking.mini_calling.agent.loop.AgentEvent;
import com.dking.mini_calling.agent.loop.AgentLoop;
import com.dking.mini_calling.agent.loop.PendingConfirmation;
import com.dking.mini_calling.agent.memory.ChatMemoryStore;
import com.dking.mini_calling.agent.tool.ToolContext;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.agent.web.dto.ConfirmWebRequest;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * agent 编排门面：会话定位 → 消息数组组装 → 交给 AgentLoop（含工具循环）→ 时间线写回记忆。
 * Phase 5：危险操作挂起入库，人工裁决后从快照恢复——
 * 工具的执行与确认闸都在 AgentLoop 里（executeTool / runToolCalls 两个唯一出口），
 * 本类只负责"组装进、裁决转发、存回来"，不再触碰任何工具调用细节。
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

    private final AgentProperties agentProperties;
    private final AgentLoop agentLoop;
    private final ChatMemoryStore memoryStore;
    private final PendingActionStore actionStore;

    public AgentService(AgentProperties agentProperties, AgentLoop agentLoop,
                        ChatMemoryStore memoryStore, PendingActionStore actionStore) {
        this.agentProperties = agentProperties;
        this.agentLoop = agentLoop;
        this.memoryStore = memoryStore;
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
            return suspendForConfirm(user, sessionId, result, beforeTurn, sink);
        }

        // 本轮完整时间线成段入库（含 assistant(tool_calls) 和 tool 消息），下一轮才能带着工具上下文
        memoryStore.append(sessionId,
                messages.subList(beforeTurn, messages.size()).toArray(new ChatMessage[0]));

        return new ChatWebResponse(result.reply(), sessionId, result.traces(), null);
    }

    /**
     * Phase 5：人工裁决后恢复挂起的循环。
     * approve = AgentLoop.resume：真实执行被挂起的工具（其余工具照常过确认闸），继续循环；
     * reject  = AgentLoop.reject：以 {"error":"用户拒绝"} 作为 tool 消息回填（协议一致性），模型生成婉拒答复。
     * 执行与确认闸的细节全在循环里，这里只做"转交 + 记忆截取入库"
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

        List<ChatMessage> messages = new ArrayList<>(action.confirmation().messagesSnapshot());
        PendingConfirmation pending = action.confirmation();   // 组合的红利：不再需要五字段手工重建

        AgentLoop.LoopResult result = approve
                ? agentLoop.resume(pending, messages, new ToolContext(user), sink)
                : agentLoop.reject(pending, messages, new ToolContext(user), sink);

        // 恢复后的完整时间线从 baseCount（system+历史的分界）开始截取入库，不重不漏
        memoryStore.append(action.sessionId(),
                messages.subList(action.baseCount(), messages.size()).toArray(new ChatMessage[0]));

        // 确认后模型又点名了另一个危险工具 → 再次挂起，前端再次弹确认卡片（链式确认）
        if (result.pending() != null) {
            return suspendForConfirm(user, action.sessionId(), result, action.baseCount(), sink);
        }

        return new ChatWebResponse(result.reply(), action.sessionId(), result.traces(), null);
    }

    /** 挂起入库 + 通知前端（doChat 首次挂起与 confirm 链式挂起共用这一个出口） */
    private ChatWebResponse suspendForConfirm(LoginUser user, String sessionId,
                                              AgentLoop.LoopResult result, int baseCount,
                                              Consumer<AgentEvent> sink) {
        PendingConfirmation pending = result.pending();
        PendingActionStore.PendingAction action = actionStore.create(
                user.getUserId(), sessionId, pending, baseCount);
        if (sink != null) {
            sink.accept(AgentEvent.confirmRequired(
                    action.pendingId(), pending.toolName(), pending.toolArguments()));
        }
        return new ChatWebResponse("【待确认】工具 " + pending.toolName() + " 等待人工确认",
                sessionId, result.traces(),
                new ChatWebResponse.Confirmation(
                        action.pendingId(), pending.toolName(), pending.toolArguments()));
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

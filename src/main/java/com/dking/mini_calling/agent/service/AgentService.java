package com.dking.mini_calling.agent.service;

import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.loop.AgentLoop;
import com.dking.mini_calling.agent.memory.ChatMemoryStore;
import com.dking.mini_calling.agent.tool.ToolContext;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.common.exception.BusinessException;
import com.dking.mini_calling.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * agent 编排门面：会话定位 → 消息数组组装 → 交给 AgentLoop（含工具循环）→ 时间线写回记忆。
 * Phase 2 起调 LLM 的职责移交给 AgentLoop，这里只负责"组装进、存回来"。
 */
@Service
public class AgentService {

    /** 系统提示词：给模型设定角色 + 工具使用纪律（Phase 2 新增后半段） */
    private static final String SYSTEM_PROMPT =
            "你是 mini_calling 系统的管理助手，帮助管理员查询和管理系统的用户与角色。"
                    + "需要查询或操作系统数据时，优先调用提供的工具，不要凭空编造数据；"
                    + "请用简洁的中文回答。";

    private final AgentProperties agentProperties;
    private final AgentLoop agentLoop;
    private final ChatMemoryStore memoryStore;

    public AgentService(AgentProperties agentProperties, AgentLoop agentLoop, ChatMemoryStore memoryStore) {
        this.agentProperties = agentProperties;
        this.agentLoop = agentLoop;
        this.memoryStore = memoryStore;
    }

    public ChatWebResponse chat(LoginUser user, ChatWebRequest request) {
        String sessionId = resolveOrCreate(user, request.sessionId());

        // 模型无状态：每次请求都要重发完整数组 = system 人设 + 该会话历史 + 本轮问题
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.addAll(memoryStore.history(sessionId));

        int beforeTurn = messages.size();   // 此下标之后全是本轮新增（user → 工具链 → 最终回答）

        messages.add(ChatMessage.user(request.message()));

        AgentLoop.LoopResult result = agentLoop.run(messages, new ToolContext(user));

        // 本轮完整时间线成段入库（含 assistant(tool_calls) 和 tool 消息），下一轮才能带着工具上下文
        memoryStore.append(sessionId,
                messages.subList(beforeTurn, messages.size()).toArray(new ChatMessage[0]));

        return new ChatWebResponse(result.reply(), sessionId, result.traces());
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

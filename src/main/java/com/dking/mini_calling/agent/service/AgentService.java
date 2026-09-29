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
import com.dking.mini_calling.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * agent 编排门面：会话定位 → 消息数组组装 → 调 LLM → 结果写回记忆。
 * Controller 只做协议转换，业务编排从 Phase 1 起下沉到这里。
 */
@Service
public class AgentService {

    /** 系统提示词：给模型设定角色。Phase 2/3 会追加工具使用纪律 */
    private static final String SYSTEM_PROMPT =
            "你是 mini_calling 系统的管理助手，帮助管理员查询和管理系统的用户与角色。请用简洁的中文回答。";

    private final AgentProperties agentProperties;
    private final LlmClient llmClient;
    private final ChatMemoryStore memoryStore;

    public AgentService(AgentProperties agentProperties, LlmClient llmClient, ChatMemoryStore memoryStore) {
        this.agentProperties = agentProperties;
        this.llmClient = llmClient;
        this.memoryStore = memoryStore;
    }

    public ChatWebResponse chat(LoginUser user, ChatWebRequest request) {
        String sessionId = resolveOrCreate(user, request.sessionId());

        // 模型无状态：每次请求都要重发完整数组 = system 人设 + 该会话历史 + 本轮问题
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.addAll(memoryStore.history(sessionId));
        messages.add(ChatMessage.user(request.message()));

        ChatResponse response = llmClient.chat(ChatRequest.of(agentProperties, messages));
        String reply = response.firstContent();

        // 本轮问答成对入库，供下一轮拼装（Phase 2 的 tool 消息也会进这条时间线）
        memoryStore.append(sessionId,
                ChatMessage.user(request.message()),
                ChatMessage.assistant(reply, null));

        return new ChatWebResponse(reply, sessionId);
    }

    /**
     * 会话归属校验：sessionId 必须以自己的 userId 开头。
     * 没有这行，拿到别人 sessionId 的请求就能读走别人的对话历史。
     */
    private String resolveOrCreate(LoginUser user, String sessionId) {
        // sessionId 是空,则创建一个。
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

package com.dking.mini_calling.agent.web;

import com.dking.mini_calling.agent.client.LlmClient;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "AI 助手", description = "用自然语言管理用户与角色（agent 模块，按阶段增量生长）")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    /** 系统提示词：给模型设定角色。Phase 2/3 会追加工具使用纪律 */
    private static final String SYSTEM_PROMPT =
            "你是 mini_calling 系统的管理助手，帮助管理员查询和管理系统的用户与角色。请用简洁的中文回答。";

    private final AgentProperties agentProperties;
    private final LlmClient llmClient;

    @Operation(summary = "AI 对话", description = "单轮对话；需登录（JWT），Phase 1 升级为多轮会话")
    @PostMapping("/chat")
    public ChatWebResponse chat(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @RequestBody ChatWebRequest request) {
        log.info("AI 对话：userId={}, username={}, question={}",
                loginUser.getUserId(), loginUser.getUsername(), request.message());

        // Phase 0 的消息数组只有两条：system 人设 + 用户这句话；多轮历史和工具循环在后续阶段加入
        ChatResponse response = llmClient.chat(ChatRequest.of(agentProperties, List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(request.message()))));

        return new ChatWebResponse(response.firstContent());
    }
}

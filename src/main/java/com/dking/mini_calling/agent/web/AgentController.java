package com.dking.mini_calling.agent.web;

import com.dking.mini_calling.agent.service.AgentService;
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

/**
 * Phase 1 重构：Controller 瘦身为纯协议转换（收请求 → 委托 → 还响应），
 * 消息组装/会话记忆等编排逻辑移到了 AgentService——业务变厚时保持入口层干净
 */
@Slf4j
@Tag(name = "AI 助手", description = "用自然语言管理用户与角色（agent 模块，按阶段增量生长）")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "AI 对话", description = "多轮会话；首轮 sessionId 传空，之后带上响应回传的 sessionId")
    @PostMapping("/chat")
    public ChatWebResponse chat(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @RequestBody ChatWebRequest request) {
        log.info("AI 对话：userId={}, username={}", loginUser.getUserId(), loginUser.getUsername());
        return agentService.chat(loginUser, request);
    }
}

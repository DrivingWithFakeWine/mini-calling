package com.dking.mini_calling.agent.springai;

import com.dking.mini_calling.common.IgnoreWrap;
import com.dking.mini_calling.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * Spring AI 框架版助手端点（Phase 6，与手写版 /agent/chat 并存对比）。
 *
 * 对比观察点：
 * - 非流式 chat() 只有十几行——协议/DTO/循环全部由框架接管；
 * - 框架版没有确认机制（human-in-the-loop）：危险操作会被直接执行，
 *   这是 Phase 6 对比表里最重要的一行"框架给了什么、没给什么"；
 * - ASYNC 收尾分发的放行已在 Phase 5.1 配置（dispatcherTypeMatchers），此端点直接受益。
 */
@Slf4j
@Tag(name = "AI 助手（Spring AI 版）", description = "同一套管理能力，由 Spring AI 框架驱动的对照实现")
@RestController
@RequestMapping("/agent/ai")
@RequiredArgsConstructor
public class SpringAiAgentController {

    private final ChatClient adminChatClient;

    public record AiChatRequest(@NotBlank(message = "消息不能为空") String message, String sessionId) {
    }

    public record AiChatResponse(String reply, String sessionId) {
    }

    /** 框架版会话 id 约定："ai-" 前缀 + userId，与手写版同样用前缀做归属校验 */
    private String resolveSessionId(LoginUser loginUser, String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            if (!sessionId.startsWith("ai-" + loginUser.getUserId() + "-")) {
                throw new IllegalArgumentException("会话不存在或已过期");
            }
            return sessionId;
        }
        return "ai-" + loginUser.getUserId() + "-" + UUID.randomUUID();
    }

    @Operation(summary = "AI 对话（Spring AI，非流式）", description = "多轮会话；sessionId 首轮传空")
    @PostMapping("/chat")
    public AiChatResponse chat(@AuthenticationPrincipal LoginUser loginUser,
                               @Valid @RequestBody AiChatRequest request) {
        String sessionId = resolveSessionId(loginUser, request.sessionId());
        String reply = adminChatClient.prompt()
                .user(request.message())
                .toolContext(Map.of("loginUser", loginUser))   // 框架版 ToolContext：工具方法由此拿到调用者身份
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .content();
        return new AiChatResponse(reply, sessionId);
    }

    @Operation(summary = "AI 对话（Spring AI，流式）", description = "SSE 推送 delta 事件；框架版无工具过程事件与确认卡片（对比点）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @IgnoreWrap
    public SseEmitter stream(@AuthenticationPrincipal LoginUser loginUser,
                             @Valid @RequestBody AiChatRequest request) {
        String sessionId = resolveSessionId(loginUser, request.sessionId());
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> log.debug("SpringAI SSE 连接结束"));
        emitter.onError(t -> log.warn("SpringAI SSE 异常断开: {}", t.getMessage()));

        Flux<String> deltas = adminChatClient.prompt()
                .user(request.message())
                .toolContext(Map.of("loginUser", loginUser))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();

        deltas.subscribe(
                delta -> send(emitter, "delta", toJson(Map.of("text", delta))),
                error -> {
                    log.warn("SpringAI 流式对话失败: msg={}", error.getMessage());
                    send(emitter, "error", toJson(Map.of("message",
                            error.getMessage() == null ? "服务异常" : error.getMessage())));
                    emitter.complete();
                },
                emitter::complete);
        return emitter;
    }

    private void send(SseEmitter emitter, String event, String json) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}

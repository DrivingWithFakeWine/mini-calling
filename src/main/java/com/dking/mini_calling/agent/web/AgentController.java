package com.dking.mini_calling.agent.web;

import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.agent.confirm.PendingActionStore;
import com.dking.mini_calling.agent.service.AgentService;
import com.dking.mini_calling.agent.web.dto.ChatWebRequest;
import com.dking.mini_calling.agent.web.dto.ChatWebResponse;
import com.dking.mini_calling.agent.web.dto.ConfirmWebRequest;
import com.dking.mini_calling.common.IgnoreWrap;
import com.dking.mini_calling.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

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
    private final AgentProperties agentProperties;
    private final ThreadPoolTaskExecutor agentExecutor;
    private final ObjectMapper objectMapper;
    private final PendingActionStore actionStore;

    @Operation(summary = "AI 对话", description = "多轮会话；首轮 sessionId 传空，之后带上响应回传的 sessionId")
    @PostMapping("/chat")
    public ChatWebResponse chat(@AuthenticationPrincipal LoginUser loginUser,
                                @Valid @RequestBody ChatWebRequest request) {
        log.info("AI 对话：userId={}, username={}", loginUser.getUserId(), loginUser.getUsername());
        return agentService.chat(loginUser, request);
    }

    /**
     * Phase 4 流式端点：SSE 推送 start/delta/tool/tool_result/error 事件。
     * 事件名即 AgentEvent.type，data 为 JSON 字符串；前端用 fetch+ReadableStream 手写解析
     * （EventSource 不能带 JWT 请求头，所以不用它）。
     */
    @Operation(summary = "AI 对话（流式）", description = "SSE 事件流：start→delta*→(tool/tool_result)*→结束；error 事件兜底")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @IgnoreWrap   // SSE 不是给前端 unwrap 的 Result 结构，明确退出全局包装（虽然 emitter 本就不会被包装）
    public SseEmitter stream(@AuthenticationPrincipal LoginUser loginUser,
                             @Valid @RequestBody ChatWebRequest request) {
        log.info("AI 流式对话：userId={}", loginUser.getUserId());

        // 纪律：鉴权/参数校验都发生在上面这段同步代码里（异常仍走全局处理器）；
        // 下面交给异步线程后，任何异常都到不了 GlobalExceptionHandler，必须自己兜成 error 事件
        SseEmitter emitter = new SseEmitter(agentProperties.getStreamTimeoutMs());
        // 生命周期可观测：异步超时以前只会在后台留下两行费解的 Security 报错（ERROR dispatch 无登录态），
        // 现在有一句人话。注意超时计的是"从创建到完成"的总时长，send 不会重置它
        emitter.onTimeout(() -> log.warn("SSE 超时断开：本轮对话总耗时超过 stream-timeout-ms={}（断点调试停留过久同样会触发）",
                agentProperties.getStreamTimeoutMs()));
        emitter.onError(t -> log.warn("SSE 异常断开: {}", t.getMessage()));
        emitter.onCompletion(() -> log.debug("SSE 连接结束"));
        agentExecutor.execute(() -> {
            try {
                agentService.chatStream(loginUser, request, event ->
                        send(emitter, event.type(), toJson(event.data())));
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式对话失败: sessionId={}, msg={}", request.sessionId(), e.getMessage());
                // 先把错误以事件形式告诉前端，再优雅收流——completeWithError 会直接掐断连接，
                // 前端 read() 抛错只能显示"网络异常"，拿不到我们准备好的错误信息
                send(emitter, "error", toJson(Map.of("message",
                        e.getMessage() == null ? "服务异常，请稍后重试" : e.getMessage())));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * Phase 5：人工裁决挂起的危险操作。approve=执行并继续对话；reject=婉拒。
     * store.consume 放在同步段：同一 pendingId 的并发确认只有一个能成功
     */
    @Operation(summary = "确认/拒绝危险操作", description = "SSE 事件流同 /chat/stream；decision=approve|reject")
    @PostMapping(value = "/chat/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @IgnoreWrap
    public SseEmitter confirm(@AuthenticationPrincipal LoginUser loginUser,
                              @Valid @RequestBody ConfirmWebRequest request) {
        log.info("危险操作裁决：userId={}, pendingId={}, decision={}",
                loginUser.getUserId(), request.pendingId(), request.decision());

        PendingActionStore.PendingAction action = actionStore.consume(request.pendingId(), loginUser.getUserId());
        SseEmitter emitter = new SseEmitter(agentProperties.getStreamTimeoutMs());
        if (action == null) {
            send(emitter, "error", toJson(Map.of("message",
                    "确认已失效或不存在（可能已过期、已处理，或不是你的待确认项）")));
            emitter.complete();
            return emitter;
        }
        if (!"approve".equalsIgnoreCase(request.decision()) && !"reject".equalsIgnoreCase(request.decision())) {
            send(emitter, "error", toJson(Map.of("message", "decision 必须是 approve 或 reject")));
            emitter.complete();
            return emitter;
        }
        agentExecutor.execute(() -> {
            try {
                agentService.confirm(loginUser, action, request.decision(), event ->
                        send(emitter, event.type(), toJson(event.data())));
                emitter.complete();
            } catch (Exception e) {
                log.warn("确认执行失败: sessionId={}, msg={}", action.sessionId(), e.getMessage());
                send(emitter, "error", toJson(Map.of("message",
                        e.getMessage() == null ? "服务异常，请稍后重试" : e.getMessage())));
                emitter.complete();
            }
        });
        return emitter;
    }

    /** SSE 发送兜底：客户端提前断开时 send 会抛 IOException，连接已死，吞掉别打断完成流程 */
    private void send(SseEmitter emitter, String event, String json) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}

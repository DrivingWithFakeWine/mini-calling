package com.dking.mini_calling.agent.confirm;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ToolCall;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待确认动作仓库：挂起的循环状态在这里等待人工裁决。
 *
 * 三条规矩：
 * - 一次性消费：consume 即移除，同一个 pendingId 二次确认直接失败（防重放）；
 * - 归属校验：userId 不匹配视为不存在（不泄露"是否存在"的信息）；
 * - TTL 过期清理：5 分钟不裁决自动作废，防止仓库无限膨胀。
 */
@Component
public class PendingActionStore {

    /** 确认有效期（分钟） */
    private static final long TTL_MINUTES = 5;

    private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();

    public record PendingAction(
            String pendingId,
            Long userId,
            String sessionId,
            List<ChatMessage> messagesSnapshot,
            int baseCount,
            String callId,
            String toolName,
            String toolArguments,
            List<ToolCall> remainingCalls,
            Instant createdAt) {
    }

    /** 创建挂起动作（pendingId 内部生成），返回完整 PendingAction；userId 存入用于 consume 时的归属校验 */
    public PendingAction create(Long userId, String sessionId, List<ChatMessage> snapshot,
                                int baseCount, String callId, String toolName,
                                String toolArguments, List<ToolCall> remainingCalls) {
        PendingAction action = new PendingAction(
                UUID.randomUUID().toString(), userId, sessionId,
                List.copyOf(snapshot), baseCount, callId, toolName, toolArguments,
                List.copyOf(remainingCalls), Instant.now());
        actions.put(action.pendingId(), action);
        return action;
    }

    /** 消费挂起动作：归属不符或已不存在都返回 null（调用方转成"已失效"提示） */
    public PendingAction consume(String pendingId, Long userId) {
        PendingAction action = actions.remove(pendingId);
        if (action == null || !action.userId().equals(userId)) {
            return null;
        }
        return action;
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant deadline = Instant.now().minusSeconds(TTL_MINUTES * 60);
        actions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(deadline));
    }
}

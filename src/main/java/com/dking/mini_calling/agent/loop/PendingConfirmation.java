package com.dking.mini_calling.agent.loop;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ToolCall;

import java.util.List;

/**
 * 挂起意图：循环遇到 needConfirm 的工具时停止执行，把"恢复所需的一切"打包返回。
 * 上层（AgentService）据此创建 PendingAction 存入仓库，等人工确认后恢复执行。
 *
 * messagesSnapshot 是挂起瞬间的完整消息数组（含铁律①的 assistant(tool_calls)
 * 和本轮已执行完的其他工具结果）。恢复方在其上执行被确认的工具、继续 run()。
 * 记忆截取起点（baseCount）由 AgentService 提供——只有它知道 system+历史 的分界，
 * 循环不该越权假装知道。
 */
public record PendingConfirmation(
        List<ChatMessage> messagesSnapshot,
        String callId,
        String toolName,
        String toolArguments,
        List<ToolCall> remainingCalls) {
}

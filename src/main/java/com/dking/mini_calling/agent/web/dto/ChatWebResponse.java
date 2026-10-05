package com.dking.mini_calling.agent.web.dto;

import java.util.List;

/**
 * 返回给前端的对话响应。
 * confirmation 非 null = 存在待人工确认的危险操作（前端渲染确认卡片）
 */
public record ChatWebResponse(String reply, String sessionId, List<ToolTrace> toolTraces, Confirmation confirmation) {

    public record Confirmation(String pendingId, String toolName, String toolArguments) {
    }
}

package com.dking.mini_calling.agent.web.dto;

import java.util.List;

/** 返回给前端的对话响应：回答 + 会话 id + 本轮的工具调用轨迹 */
public record ChatWebResponse(String reply, String sessionId, List<ToolTrace> toolTraces) {
}

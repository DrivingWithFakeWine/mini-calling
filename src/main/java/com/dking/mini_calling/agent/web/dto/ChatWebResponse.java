package com.dking.mini_calling.agent.web.dto;

/** 返回给前端的对话响应（Phase 2 增加 toolTraces 工具调用轨迹） */
public record ChatWebResponse(String reply, String sessionId) {
}

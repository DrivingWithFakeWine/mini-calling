package com.dking.mini_calling.agent.web.dto;

/** 返回给前端的对话响应（Phase 1 增加 sessionId，Phase 2 增加工具调用轨迹） */
public record ChatWebResponse(String reply) {
}

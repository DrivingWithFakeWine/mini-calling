package com.dking.mini_calling.agent.loop;

import java.util.Map;

/**
 * 循环过程事件：Controller 把它转成 SSE（event=type, data=JSON）推给前端。
 * start 先行（带 sessionId，流中断前端也有归属）；delta/tool/tool_result 过程中任意交错；error 由 Controller 兜底
 */
public record AgentEvent(String type, Map<String, Object> data) {

    public static AgentEvent start(String sessionId) {
        return new AgentEvent("start", Map.of("sessionId", sessionId));
    }

    public static AgentEvent delta(String text) {
        return new AgentEvent("delta", Map.of("text", text));
    }

    public static AgentEvent tool(String name, String arguments) {
        return new AgentEvent("tool", Map.of("tool", name, "arguments", arguments));
    }

    public static AgentEvent toolResult(String name, String summary) {
        return new AgentEvent("tool_result", Map.of("tool", name, "result", summary));
    }
}

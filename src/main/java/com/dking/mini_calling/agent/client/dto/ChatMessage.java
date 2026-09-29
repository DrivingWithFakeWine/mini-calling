package com.dking.mini_calling.agent.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 一条对话消息。OpenAI 兼容协议共四种角色：
 * system（系统指令）/ user（用户输入）/ assistant（模型回复）/ tool（工具执行结果）
 *
 * ignoreUnknown：智谱会返回 OpenAI 规范之外的字段（如思考模型的 reasoning_content），
 * 不加此注解，遇到未知字段反序列化会直接抛异常；
 * NON_NULL：请求序列化时 null 字段不输出，部分兼容端点对显式 null 敏感
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    /** 带工具调用请求的 assistant 消息（Phase 2 的 agent 循环中使用） */
    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    /** 工具执行结果：必须携带被回应的那个 tool_call 的 id，协议要求一一对应 */
    public static ChatMessage tool(String toolCallId, String resultJson) {
        return new ChatMessage("tool", resultJson, null, toolCallId);
    }
}

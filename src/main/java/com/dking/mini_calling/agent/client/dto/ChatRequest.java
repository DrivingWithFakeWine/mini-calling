package com.dking.mini_calling.agent.client.dto;

import com.dking.mini_calling.agent.config.AgentProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 发给 LLM 的对话请求（OpenAI 兼容格式）
 * NON_NULL：未启用的字段保持 null，序列化时直接省略，请求体保持最小
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        List<ToolDefinition> tools,
        Double temperature,
        Thinking thinking) {

    public static ChatRequest of(AgentProperties props, List<ChatMessage> messages) {
        return of(props, messages, null);
    }

    public static ChatRequest of(AgentProperties props, List<ChatMessage> messages, List<ToolDefinition> tools) {
        return new ChatRequest(
                props.getModel(),
                messages,
                tools,
                props.getTemperature(),
                props.isThinkingDisabled() ? Thinking.DISABLED : Thinking.ENABLED);
    }

    /** 智谱扩展参数（非 OpenAI 规范）：glm-4.6 默认开启深度思考，管理问答场景关闭可明显降低延迟 */
    public record Thinking(String type) {
        public static final Thinking DISABLED = new Thinking("disabled");
        public static final Thinking ENABLED = new Thinking("enabled");
    }
}

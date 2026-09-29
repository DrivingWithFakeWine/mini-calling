package com.dking.mini_calling.agent.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 模型发起的一次工具调用请求
 * 关键认知：function.arguments 是 JSON 字符串而不是对象——
 * 模型输出的参数文本需要我们自己解析、自己容错，这正是手写 agent 的教学价值
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCall(String id, String type, Function function) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Function(String name, String arguments) {
    }
}

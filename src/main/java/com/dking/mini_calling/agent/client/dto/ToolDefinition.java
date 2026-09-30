package com.dking.mini_calling.agent.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 发给模型的工具描述（tools 数组的一项）。
 * description 写得好不好，直接影响模型选工具的准确率——这是"工具设计学"的第一课
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDefinition(String type, Function function) {

    public static ToolDefinition function(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition("function", new Function(name, description, parameters));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Function(String name, String description, Map<String, Object> parameters) {
    }
}

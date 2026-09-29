package com.dking.mini_calling.agent.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** LLM 的对话响应（OpenAI 兼容格式） */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(String id, String model, List<Choice> choices, Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }

    /** 取首个回答文本；sensitive / 空 choices 已在 LlmClient 提前拦截，正常返回时 content 不会是 null */
    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return null;
        }
        return choices.get(0).message().content();
    }
}

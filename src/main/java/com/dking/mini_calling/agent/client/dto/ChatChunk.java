package com.dking.mini_calling.agent.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 流式响应的一个分片（OpenAI 兼容 SSE 协议）。
 * 智谱的已知坑：最后一个只含 usage 的分片 choices 是空数组——deltaText()/finishReason() 已做空值防护
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatChunk(List<Choice> choices, ChatResponse.Usage usage) {

    /** 本分片携带的文本增量（纯文本对话时使用），无则为 null */
    public String deltaText() {
        Delta d = delta();
        return d == null ? null : d.content();
    }

    public Delta delta() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).delta();
    }

    public String finishReason() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).finishReason();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Delta delta, @JsonProperty("finish_reason") String finishReason) {
    }

    /** 流式增量：文本或 tool_calls 碎片——arguments 是跨分片的碎片，需按 index 合并 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String content, @JsonProperty("tool_calls") List<DeltaToolCall> toolCalls) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeltaToolCall(Integer index, String id, String type, Function function) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Function(String name, String arguments) {
        }
    }
}

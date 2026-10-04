package com.dking.mini_calling.agent.client;

import com.dking.mini_calling.agent.client.dto.ChatChunk;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.client.dto.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 把流式分片聚合回"非流式形状"——agent 循环因此不需要感知流式与否（同一循环两种输出）。
 * 合并规则：文本增量直接拼接；tool_calls 按 index 分桶，id/name 记最后一次非空值，arguments 逐段拼接
 */
public class StreamCollector {

    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, Buffer> toolBuffers = new TreeMap<>();
    private String finishReason = "stop";
    private ChatResponse.Usage usage;

    private static class Buffer {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    public void accept(ChatChunk chunk) {
        if (chunk.usage() != null) {
            usage = chunk.usage();
        }
        if (chunk.choices() == null || chunk.choices().isEmpty()) {
            return;   // 智谱末尾的 usage 分片 choices 为空数组
        }
        ChatChunk.Choice choice = chunk.choices().get(0);
        if (choice.finishReason() != null) {
            finishReason = choice.finishReason();
        }
        ChatChunk.Delta delta = choice.delta();
        if (delta == null) {
            return;
        }
        if (delta.content() != null) {
            content.append(delta.content());
        }
        if (delta.toolCalls() != null) {
            for (ChatChunk.DeltaToolCall dtc : delta.toolCalls()) {
                Buffer buf = toolBuffers.computeIfAbsent(dtc.index() == null ? 0 : dtc.index(), k -> new Buffer());
                // dtc.id 是tool_call_id
                if (dtc.id() != null) {
                    buf.id = dtc.id();
                }
                if (dtc.function() != null) {
                    if (dtc.function().name() != null) {
                        buf.name = dtc.function().name();
                    }
                    if (dtc.function().arguments() != null) {
                        buf.arguments.append(dtc.function().arguments());
                    }
                }
            }
        }
    }

    /** 聚合结果，形状与 LlmClient.chat() 的返回一致 */
    public ChatResponse toResponse() {
        List<ToolCall> calls = new ArrayList<>();
        for (Buffer buf : toolBuffers.values()) {
            calls.add(new ToolCall(buf.id, "function", new ToolCall.Function(buf.name, buf.arguments.toString())));
        }
        String text = content.isEmpty() ? null : content.toString();
        List<ToolCall> toolCalls = calls.isEmpty() ? null : calls;
        ChatMessage assistant = new ChatMessage("assistant", text, toolCalls, null);
        return new ChatResponse(null, null, List.of(new ChatResponse.Choice(assistant, finishReason)), usage);
    }
}

package com.dking.mini_calling.agent.memory;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个会话的有状态持有物：这个 sessionId 下的全部对话消息。
 *
 * 两个必须理解的限制：
 * 1. 存在内存里，应用重启即全部丢失（持久化到 Redis/DB 留作扩展练习）；
 * 2. LLM 本身仍然无状态——这里的"记忆"只是我们把历史重新拼进每次请求的原料。
 */
@Getter
public class ChatSession {

    private final String sessionId;
    private final List<ChatMessage> messages = new ArrayList<>();
    private long lastAccessAt = System.currentTimeMillis();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public void touch() {
        this.lastAccessAt = System.currentTimeMillis();
    }
}

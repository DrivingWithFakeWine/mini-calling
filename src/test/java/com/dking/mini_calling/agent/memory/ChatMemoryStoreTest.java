package com.dking.mini_calling.agent.memory;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatMemoryStore 单元测试：手工 new、不走 Spring、毫秒级完成。
 * TTL 测试技巧：把 sessionTtlMinutes 配成 0，evictExpired() 的条件 now-lastAccess >= 0 恒成立，
 * 不用真的等 30 分钟
 */
class ChatMemoryStoreTest {

    private AgentProperties props;
    private ChatMemoryStore store;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        props.setHistoryLimit(4);
        props.setSessionTtlMinutes(0);   // TTL=0：手动调 evictExpired() 时必然全清，测试确定性
        store = new ChatMemoryStore(props);
    }

    @Test
    @DisplayName("正例：追加后能按顺序读回完整历史")
    void append_and_history_roundtrip() {
        String sid = store.create(1L);
        store.append(sid, ChatMessage.user("q1"), ChatMessage.assistant("a1", null));
        store.append(sid, ChatMessage.user("q2"), ChatMessage.assistant("a2", null));

        var history = store.history(sid);
        assertThat(history).hasSize(4);
        assertThat(history.get(0).content()).isEqualTo("q1");
        assertThat(history.get(3).content()).isEqualTo("a2");
    }

    @Test
    @DisplayName("正例：未知 sessionId 读历史返回空列表而不是 NPE")
    void history_unknown_session_empty() {
        assertThat(store.history("ghost")).isEmpty();
        store.append("ghost", ChatMessage.user("hi"));   // append 到不存在的会话：静默忽略不抛异常
        assertThat(store.history("ghost")).isEmpty();
    }

    @Test
    @DisplayName("正例：超过 historyLimit 丢最旧的，总量稳定在阈值内")
    void truncation_drops_oldest() {
        String sid = store.create(1L);
        store.append(sid, ChatMessage.user("q1"), ChatMessage.assistant("a1", null));
        store.append(sid, ChatMessage.user("q2"), ChatMessage.assistant("a2", null));
        store.append(sid, ChatMessage.user("q3"), ChatMessage.assistant("a3", null));

        store.append(sid, ChatMessage.user("q4"), ChatMessage.assistant("a4", null));   // 8 条 → 裁到 4

        var history = store.history(sid);
        assertThat(history).hasSize(4);
        assertThat(history.get(0).content()).isEqualTo("q3");   // q1/a1 已被丢掉
    }

    @Test
    @DisplayName("正例：TTL 过期后会被清理")
    void evict_removes_expired() {
        String sid = store.create(1L);
        store.append(sid, ChatMessage.user("hi"), ChatMessage.assistant("ok", null));

        store.evictExpired();   // TTL=0 → 全部过期

        assertThat(store.exists(sid)).isFalse();
        assertThat(store.history(sid)).isEmpty();
    }

    @Test
    @DisplayName("正例：截断切在工具链中间时，丢掉头部孤儿 tool 消息（协议要求 tool 紧跟 assistant(tool_calls)）")
    void truncation_never_leaves_orphan_tool() {
        props.setHistoryLimit(2);
        String sid = store.create(1L);
        store.append(sid,
                ChatMessage.user("u1"),
                ChatMessage.assistant("调工具中", List.of()),
                ChatMessage.tool("c1", "{\"ok\":true}"),
                ChatMessage.assistant("final", null));

        var history = store.history(sid);

        assertThat(history.get(0).role()).isNotEqualTo("tool");
        assertThat(history.get(history.size() - 1).content()).isEqualTo("final");
    }
}

package com.dking.mini_calling.agent.memory;

import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.config.AgentProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆存储：sessionId → 对话消息列表。
 *
 * 设计要点：
 * - sessionId = "userId-UUID"：前缀天然标识归属，鉴权只需比对前缀，不需要查库；
 * - history() 返回不可变副本，外部改不动内部状态；
 * - 超限丢最旧（historyLimit）：最朴素的截断策略，按 token 计数淘汰是进阶话题；
 * - @Scheduled 定期清理过期会话，防止内存无限增长。
 */
@Component
public class ChatMemoryStore {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final AgentProperties props;

    public ChatMemoryStore(AgentProperties props) {
        this.props = props;
    }

    /** 新建会话，返回生成的 sessionId */
    public String create(Long userId) {
        String sessionId = userId + "-" + UUID.randomUUID();
        sessions.put(sessionId, new ChatSession(sessionId));
        return sessionId;
    }

    /** 已知 id 的会话不存在时原地重建（服务重启/TTL 过期后客户端还拿着旧 id 的场景） */
    public void ensure(String sessionId) {
        sessions.computeIfAbsent(sessionId, ChatSession::new);
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /** 返回不可变副本：调用方拼请求用，改不动内部状态 */
    public List<ChatMessage> history(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        return session == null ? List.of() : List.copyOf(session.getMessages());
    }

    /** 追加消息并刷新活跃时间；超过 historyLimit 丢最旧的 */
    public synchronized void append(String sessionId, ChatMessage... messages) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return;   // 防御：正常流程里 resolve 已保证会话存在
        }
        for (ChatMessage message : messages) {
            if (message != null) {
                session.getMessages().add(message);
            }
        }
        session.touch();
        List<ChatMessage> list = session.getMessages();
        while (list.size() > props.getHistoryLimit()) {
            list.remove(0);
        }
    }

    /** 每分钟清理一次空闲超 TTL 的会话（TTL=0 意为立即全部过期，测试用） */
    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        long ttlMs = props.getSessionTtlMinutes() * 60_000L;
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().getLastAccessAt() >= ttlMs);
    }
}

package com.dking.mini_calling.agent.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 框架版助手的装配。
 *
 * 对比观察点：ChatClient 一行调用替代了手写版的 LlmClient + ChatChunk + StreamCollector + AgentLoop——
 * 框架接管了 HTTP/SSE 协议、DTO、分片聚合、tool_calls 循环这些"协议翻译"层；
 * 而 SYSTEM_PROMPT、工具语义、安全校验依然是我们的业务输入，框架不会替你思考。
 */
@Configuration
public class SpringAiConfig {

    /** 框架版系统提示词：与手写版 AgentService.SYSTEM_PROMPT 保持同源（角色纪律一致，对比才公平） */
    public static final String SYSTEM_PROMPT =
            "你是 mini_calling 系统的管理助手，帮助管理员查询和管理系统的用户与角色。"
                    + "需要查询或操作系统数据时，优先调用提供的工具，不要凭空编造数据；"
                    + "提到角色/权限时一律使用名称，id 由你调用查询工具获得，绝不猜测；"
                    + "请用简洁的中文回答。";

    @Bean
    public ChatMemory chatMemory() {
        // 默认窗口 20 条——与手写版 ChatMemoryStore 的 historyLimit=20 对齐，对比才公平
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    @Bean
    public ChatClient adminChatClient(OpenAiChatModel chatModel, AdminTools adminTools, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(adminTools)   // @Tool 方法自动生成 schema、绑定参数、执行调用
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}

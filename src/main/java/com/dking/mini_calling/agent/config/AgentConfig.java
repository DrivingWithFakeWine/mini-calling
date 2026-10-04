package com.dking.mini_calling.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * agent 模块的配置入口。
 * Phase 1：@EnableScheduling 驱动 ChatMemoryStore 的过期会话清理；
 * Phase 4：agentExecutor 线程池——流式对话的循环跑在异步线程上，Servlet 请求线程立即释放
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    @Bean
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-stream-");
        return executor;
    }
}

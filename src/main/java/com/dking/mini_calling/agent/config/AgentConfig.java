package com.dking.mini_calling.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * agent 模块的配置入口。
 * 后续阶段会在这里追加：@EnableScheduling（P1 会话过期清理）、agentExecutor 线程池（P4 流式）
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
}

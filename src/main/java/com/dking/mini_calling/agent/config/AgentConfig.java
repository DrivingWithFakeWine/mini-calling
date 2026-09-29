package com.dking.mini_calling.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * agent 模块的配置入口。
 * Phase 1：@EnableScheduling 驱动 ChatMemoryStore 的过期会话清理；
 * 后续阶段会在这里追加 agentExecutor 线程池（P4 流式）
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
}

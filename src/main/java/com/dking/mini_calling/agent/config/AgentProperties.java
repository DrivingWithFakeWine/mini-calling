package com.dking.mini_calling.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * agent 模块配置（对应 application.yml 的 agent: 段）
 * 每个字段都给默认值：即使 yml 一字不配，应用也能启动（apiKey 是 dummy，真正调用 LLM 时才报错）
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** LLM API 基础地址（OpenAI 兼容端点，不含 /chat/completions 后缀） */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** API Key：真实值走环境变量 ZHIPU_API_KEY，绝不提交进 git */
    private String apiKey = "dummy-key";

    private String model = "glm-4.6";

    private Double temperature = 0.6;

    /** true = 关闭 glm-4.6 的深度思考：管理问答用不上，换取更快的首字响应 */
    private boolean thinkingDisabled = true;

    private long connectTimeoutMs = 5000;

    /** LLM 生成慢，读超时必须远大于普通接口 */
    private long readTimeoutMs = 120000;
}

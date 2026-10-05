package com.dking.mini_calling.agent.client;

import com.dking.mini_calling.agent.client.dto.ChatChunk;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * LLM 客户端：对 OpenAI 兼容 HTTP 接口的薄封装
 * 职责边界：只负责 HTTP 交互与错误转译，不做业务（消息组装在 service，工具循环在 loop 包）
 */
@Slf4j
@Component
public class LlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Spring 注入：RestClient.Builder 是 Boot 自动配置的原型 bean，自带 Jackson 消息转换器。
     *  类里有两个构造器时，必须用 @Autowired 明确告诉 Spring 用哪个，否则启动报 No default constructor */
    @Autowired
    public LlmClient(RestClient.Builder builder, AgentProperties props) {
        this(baseRestClient(builder, props)
                .requestFactory(requestFactory(props))
                .build());
    }

    /** 测试专用：注入绑定了 MockRestServiceServer 的 RestClient，单测完全不触网 */
    LlmClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** baseUrl + 鉴权头。生产与测试共用这同一段接线，避免两边各写一份导致漂移 */
    static RestClient.Builder baseRestClient(RestClient.Builder builder, AgentProperties props) {
        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
    }

    public ChatResponse chat(ChatRequest request) {
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (HttpStatusCodeException e) {
            // 4xx/5xx：上游明确拒绝了，转译成人能看懂的业务异常
            throw translateHttpError(e);
        } catch (ResourceAccessException e) {
            // 超时 / 连不上：底层是 IOException，直接抛出去接口层没法处理
            throw new BusinessException("调用大模型接口超时或网络异常，请稍后重试");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new BusinessException("大模型没有返回内容");
        }
        // sensitive 是智谱私有枚举（OpenAI 没有）：内容被安全审核拦截时 HTTP 仍是 200，必须显式处理
        if ("sensitive".equalsIgnoreCase(response.choices().get(0).finishReason())) {
            throw new BusinessException("回答被内容安全审核拦截，请调整提问后重试");
        }

        // 响应里的 model 是服务端回显的"实际使用的模型"——配置写了什么不算数，以这里为准
        log.info("LLM 响应：model={}, totalTokens={}", response.model(),
                response.usage() == null ? "无统计" : response.usage().totalTokens());
        return response;
    }

    /**
     * 流式对话：每收到一个分片回调一次 onChunk；聚合职责在调用方（StreamCollector）。
     * SSE 行协议解析在这里：data: {...} 为载荷，data: [DONE] 结束
     */
    public void chatStream(ChatRequest request, Consumer<ChatChunk> onChunk) {
        try {
            restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(request)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw translate(res.getStatusCode().value(), body);
                        }
                        readSseLines(res.getBody(), onChunk);
                        return null;
                    });
        } catch (HttpStatusCodeException e) {
            throw translateHttpError(e);
        } catch (ResourceAccessException | UncheckedIOException e) {
            // 流中途断网/读超时也走同一句人话
            throw new BusinessException("调用大模型接口超时或网络异常，请稍后重试");
        }
    }

    private void readSseLines(InputStream body, Consumer<ChatChunk> onChunk) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;   // 忽略空行、注释行等非载荷
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    return;
                }
                if (!payload.isEmpty()) {
                    onChunk.accept(objectMapper.readValue(payload, ChatChunk.class));
                }
            }
        }
    }

    private BusinessException translateHttpError(HttpStatusCodeException e) {
        return translate(e.getStatusCode().value(), e.getResponseBodyAsString());
    }

    private BusinessException translate(int status, String body) {
        // Phase 5 可观测性：错误以前只到前端、后台无痕迹（排障盲区）。现在留一条含上游摘要的日志
        log.warn("LLM 调用失败: status={}, upstream={}", status, abbreviate(body));
        if (status == 401 || status == 403) {
            return new BusinessException("LLM API Key 无效或未授权，请检查 ZHIPU_API_KEY 配置");
        }
        if (status == 429) {
            return new BusinessException("LLM 调用频率超限，请稍后重试");
        }
        // 智谱错误体形如 {"error":{"code":"1211","message":"您的模型名称不正确..."}}
        String detail = extractUpstreamMessage(body);
        return new BusinessException("LLM 调用失败(HTTP " + status + ")" + (detail == null ? "" : "：" + detail));
    }

    /** 日志里只留上游响应摘要，完整响应体进日志会成为另一个泄露面 */
    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private String extractUpstreamMessage(String body) {
        try {
            return objectMapper.readTree(body).path("error").path("message").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** LLM 生成可能要几十秒：必须显式超时，RestClient 默认无限等待 */
    private static SimpleClientHttpRequestFactory requestFactory(AgentProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getConnectTimeoutMs());
        factory.setReadTimeout((int) props.getReadTimeoutMs());
        return factory;
    }
}

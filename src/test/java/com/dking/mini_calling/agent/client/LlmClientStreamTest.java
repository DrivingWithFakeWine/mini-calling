package com.dking.mini_calling.agent.client;

import com.dking.mini_calling.agent.client.dto.ChatChunk;
import com.dking.mini_calling.agent.client.dto.ChatMessage;
import com.dking.mini_calling.agent.client.dto.ChatRequest;
import com.dking.mini_calling.agent.client.dto.ChatResponse;
import com.dking.mini_calling.agent.config.AgentProperties;
import com.dking.mini_calling.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpStatus;

/**
 * LlmClient 流式链路测试：MockRestServiceServer 直接返回 SSE 格式响应体，
 * 验证 SSE 行解析、分片转发、错误转译——与真实智谱的流式行为同构
 */
class LlmClientStreamTest {

    private static final String CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        AgentProperties props = new AgentProperties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmClient(LlmClient.baseRestClient(builder, props).build());
    }

    private ChatRequest request() {
        // 测的是 chatStream，请求就必须按流式构造（stream=true）——和生产 AgentLoop 的调用方式一致
        return ChatRequest.streaming(new AgentProperties(), List.of(ChatMessage.user("你好")), null);
    }

    /** 把若干分片 JSON 拼成一段 SSE 响应体（data: ...\n\n 序列） */
    private static String sse(String... chunkJsons) {
        StringBuilder sb = new StringBuilder();
        for (String c : chunkJsons) {
            sb.append("data: ").append(c).append("\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String textChunk(String text) {
        return "{\"choices\":[{\"delta\":{\"content\":\"" + text + "\"}}]}";
    }

    private static String toolStartChunk(String id, String name) {
        return "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"" + id
                + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":\"\"}}]}}]}";
    }

    /** partialArgs 里的引号/反斜杠会被正确 JSON 转义后再嵌入 */
    private static String toolArgsChunk(String partialArgs) {
        String escaped = partialArgs.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\""
                + escaped + "\"}}]}}]}";
    }

    private static String finishChunk(String reason) {
        return "{\"choices\":[{\"delta\":{},\"finish_reason\":\"" + reason + "\"}]}";
    }

    @Test
    @DisplayName("正例：SSE 分片逐个转发，StreamCollector 聚合出完整回答与 finish_reason；请求体带 stream=true")
    void stream_textDeltas_aggregated() {
        server.expect(once(), requestTo(CHAT_URL))
                .andExpect(jsonPath("$.stream").value(true))   // Phase 4 踩过的坑：漏了它智谱直接 400
                .andRespond(withSuccess(sse(
                        textChunk("你"),
                        textChunk("好"),
                        "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}"
                ), MediaType.TEXT_EVENT_STREAM));

        List<String> deltas = new ArrayList<>();
        StreamCollector collector = new StreamCollector();
        client.chatStream(request(), chunk -> {
            collector.accept(chunk);
            if (chunk.deltaText() != null) {
                deltas.add(chunk.deltaText());
            }
        });

        ChatResponse resp = collector.toResponse();
        assertThat(deltas).containsExactly("你", "好");
        assertThat(resp.choices().get(0).message().content()).isEqualTo("你好");
        assertThat(resp.choices().get(0).finishReason()).isEqualTo("stop");
    }

    @Test
    @DisplayName("正例：tool_calls 的 arguments 跨 3 个分片按 index 合并成完整 JSON")
    void stream_toolCallFragments_merged() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess(sse(
                        toolStartChunk("c1", "echo"),
                        toolArgsChunk("{\"te"),
                        toolArgsChunk("xt\":\"hi\"}"),
                        finishChunk("tool_calls")
                ), MediaType.TEXT_EVENT_STREAM));

        StreamCollector collector = new StreamCollector();
        client.chatStream(request(), collector::accept);

        ChatMessage assistant = collector.toResponse().choices().get(0).message();
        assertThat(assistant.toolCalls()).hasSize(1);
        assertThat(assistant.toolCalls().get(0).id()).isEqualTo("c1");
        assertThat(assistant.toolCalls().get(0).function().name()).isEqualTo("echo");
        assertThat(assistant.toolCalls().get(0).function().arguments()).isEqualTo("{\"text\":\"hi\"}");
    }

    @Test
    @DisplayName("正例（智谱语义）：末尾只含 usage 的分片 choices 为空数组——不炸且记下用量")
    void stream_usageOnlyChunk_tolerated() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess(sse(
                        textChunk("ok"),
                        "{\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,\"total_tokens\":10}}"
                ), MediaType.TEXT_EVENT_STREAM));

        StreamCollector collector = new StreamCollector();
        client.chatStream(request(), collector::accept);

        assertThat(collector.toResponse().usage().totalTokens()).isEqualTo(10);
        assertThat(collector.toResponse().choices().get(0).message().content()).isEqualTo("ok");
    }

    @Test
    @DisplayName("反例：上游 400 → 转译为带上游消息的 BusinessException")
    void stream_upstreamError_translated() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"1211\",\"message\":\"您的模型名称不正确\"}}"));

        assertThatThrownBy(() -> client.chatStream(request(), c -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("模型名称");
    }

    @Test
    @DisplayName("反例：流中途读超时/断连 → 转译为人话 BusinessException")
    void stream_timeout_translated() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.chatStream(request(), c -> {}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时");
    }
}

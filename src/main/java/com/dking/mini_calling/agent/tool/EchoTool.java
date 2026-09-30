package com.dking.mini_calling.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 玩具工具②：echo。带一个必填参数，用来观察"模型传参"这个环节长什么样 */
@Component
public class EchoTool implements AgentTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "原样返回输入的文本，用于测试工具链路是否通畅";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("text", Map.of(
                        "type", "string",
                        "description", "要原样返回的文本")),
                "required", List.of("text"));
    }

    @Override
    public String requiredPermission() {
        return "";
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        String text = args.path("text").asText("");
        return objectMapper.writeValueAsString(Map.of("echo", text));
    }
}

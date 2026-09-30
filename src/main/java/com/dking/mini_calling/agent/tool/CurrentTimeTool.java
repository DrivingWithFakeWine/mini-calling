package com.dking.mini_calling.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 玩具工具①：查当前时间。让 Phase 2 的循环先在"零业务风险"的工具上跑通 */
@Component
public class CurrentTimeTool implements AgentTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "获取服务器当前时间。当用户询问现在几点、今天日期，或需要时间戳时使用";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String requiredPermission() {
        return "";
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        return "{\"time\": \"" + LocalDateTime.now().format(FMT) + "\"}";
    }
}

package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.agent.client.dto.ToolDefinition;
import com.dking.mini_calling.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：Spring 把所有 AgentTool 实现类收集进 List 注入进来，这就是"自动注册"——
 * 不需要注解扫描的魔法，新增工具对这里是零改动。
 *
 * 铁律②的落点：invoke 对一切失败（未知工具/参数不合法/无权限/执行异常）都返回
 * {"error": ...} 给模型，而不是向上抛异常——模型有机会换参数重试或向用户解释。
 * 只有基础设施故障（LLM 连不上）才该抛异常终止。
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolRegistry(List<AgentTool> allTools) {
        for (AgentTool tool : allTools) {
            AgentTool prev = tools.putIfAbsent(tool.name(), tool);
            if (prev != null) {
                // 启动即失败好过运行期莫名选错工具
                throw new BusinessException("注册工具重名: " + tool.name());
            }
        }
    }

    /** 发给模型的 tools 数组；LinkedHashMap 保证顺序稳定（有利于将来的提示词缓存） */
    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(t -> ToolDefinition.function(t.name(), t.description(), t.parametersSchema()))
                .toList();
    }

    public String invoke(String name, String argumentsJson, ToolContext ctx) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return errorJson("未知工具: " + name);
        }
        JsonNode args;
        try {
            args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (Exception e) {
            return errorJson("参数不是合法 JSON，请检查后重试");
        }
        if (!hasPermission(tool, ctx)) {
            return errorJson("无权限执行 " + name + "（需要 " + tool.requiredPermission() + "）");
        }
        try {
            return tool.execute(args, ctx);
        } catch (Exception e) {
            return errorJson("工具执行失败: " + e.getMessage());
        }
    }

    private boolean hasPermission(AgentTool tool, ToolContext ctx) {
        if (!StringUtils.hasText(tool.requiredPermission())) {
            return true;   // 空串 = 不限权限
        }
        if (ctx == null || ctx.caller() == null) {
            return false;
        }
        return ctx.caller().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(tool.requiredPermission()));
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            return "{\"error\":\"internal\"}";
        }
    }
}

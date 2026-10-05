package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.agent.client.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 一个 agent 工具的契约。新增工具 = 新建一个 @Component 实现类，注册表零改动。
 *
 * 四个描述方法（name/description/parametersSchema）会被原样发给模型，
 * 模型靠它们决定"调不调你、传什么参数"——所以描述的质量就是工具的质量；
 * requiredPermission 留空表示不限权限，Phase 3 接真实管理能力时才填。
 */
public interface AgentTool {

    /** 工具名，模型通过它点名调用。用蛇形命名（get_current_time），模型对这种格式的遵循最好 */
    String name();

    /** 给模型看的使用说明：干什么、什么时候该用、参数含义 */
    String description();

    /** 参数的 JSON Schema（最小结构即可：type/properties/required） */
    Map<String, Object> parametersSchema();

    /** 执行所需权限码（与 Controller @PreAuthorize 的字符串保持一致），空串 = 不限 */
    String requiredPermission();

    /**
     * 是否为危险操作、执行前需要人工确认（Phase 5 的 human-in-the-loop）。
     * 用 default 方法演进接口：存量工具零改动，危险工具覆写为 true
     */
    default boolean needConfirm() {
        return false;
    }

    /**
     * 执行工具。
     *
     * @param args 模型传来的参数（已是解析好的 JSON 树，取值前仍应做存在性/类型校验——模型会传错）
     * @return 返回给模型看的 JSON 字符串。信息密度原则：最小够用，不要把整个实体吐回去
     */
    String execute(JsonNode args, ToolContext ctx) throws Exception;
}

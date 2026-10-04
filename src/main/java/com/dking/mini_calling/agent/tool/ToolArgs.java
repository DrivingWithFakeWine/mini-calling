package com.dking.mini_calling.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 从模型的参数 JSON 里取值的小工具。
 * 模型传参千奇百怪：缺字段、传 null、类型不对都可能发生——取值必须防御，不能裸调 asText()
 */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static String text(JsonNode args, String field) {
        JsonNode n = args.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    public static Long longValue(JsonNode args, String field) {
        JsonNode n = args.get(field);
        return (n == null || n.isNull()) ? null : n.asLong();
    }

    public static int intOrDefault(JsonNode args, String field, int defaultValue) {
        JsonNode n = args.get(field);
        return (n == null || n.isNull()) ? defaultValue : n.asInt(defaultValue);
    }

    public static List<Long> longList(JsonNode args, String field) {
        JsonNode n = args.get(field);
        if (n == null || n.isNull() || !n.isArray()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        n.forEach(item -> out.add(item.asLong()));
        return out;
    }

    /** 判断字段是否存在（区分"没传"和"传了空数组"——对 assign_roles 这两者语义不同） */
    public static boolean has(JsonNode args, String field) {
        JsonNode n = args.get(field);
        return n != null && !n.isNull();
    }
}

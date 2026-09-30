package com.dking.mini_calling.agent.web.dto;

import java.util.List;

/** 一次工具调用的轨迹（调试可见性：模型用了什么工具、传了什么参数、拿回了什么） */
public record ToolTrace(String tool, String arguments, String resultSummary) {
}

package com.dking.mini_calling.agent.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 前端发来的对话请求体。
 * sessionId 首次传 null/不传，服务端创建后随响应回传，之后每轮带上
 */
public record ChatWebRequest(@NotBlank(message = "消息不能为空") String message, String sessionId) {
}

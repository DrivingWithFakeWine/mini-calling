package com.dking.mini_calling.agent.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 前端发来的对话请求体 */
public record ChatWebRequest(@NotBlank(message = "消息不能为空") String message) {
}

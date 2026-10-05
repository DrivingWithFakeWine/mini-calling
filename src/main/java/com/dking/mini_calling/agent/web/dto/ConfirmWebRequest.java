package com.dking.mini_calling.agent.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 人工裁决请求：approve=执行被挂起的工具并继续对话；reject=拒绝并让模型生成婉拒答复 */
public record ConfirmWebRequest(@NotBlank(message = "pendingId 不能为空") String pendingId,
                                @NotBlank(message = "decision 不能为空") String decision) {
}

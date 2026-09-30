package com.dking.mini_calling.agent.tool;

import com.dking.mini_calling.security.LoginUser;

/**
 * 工具执行时的环境信息，显式传参、不依赖 SecurityContextHolder。
 *
 * 为什么不直接在工具里读 SecurityContext？Phase 4 起循环会跑在自建线程池上，
 * ThreadLocal 不会跟过去——到时候再改就是埋雷，第一天就立好"显式传递"的规矩
 */
public record ToolContext(LoginUser caller) {
}

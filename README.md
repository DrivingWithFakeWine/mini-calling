# mini_calling

一个**边学边做**的 AI Agent 教学项目：在 Spring Boot RBAC 权限系统之上，从零手写一个
LLM 管理助手，并引入 Spring AI 框架版做同能力对照——最终回答"**大模型应用的每一层
到底是谁在做什么**"。

两套实现并存，行为可对比：

| 端点 | 实现 | 说明 |
|------|------|------|
| `POST /agent/chat` / `/agent/chat/stream` | **手写版**：自研 function calling 循环 | 每一层协议、每一条铁律都看得见 |
| `POST /agent/ai/chat` / `/agent/ai/chat/stream` | **框架版**：Spring AI `@Tool` + ChatClient | 同一套工具，框架接管协议层 |
| `POST /agent/chat/confirm` | 确认裁决 | 危险操作需人工批准/拒绝 |

## 功能特性

- 🔐 **RBAC 权限系统**：用户/角色/权限三表模型，JWT 认证，方法级 `@PreAuthorize`
- 🤖 **自然语言管理**：查用户、建用户、启停账号、删用户、改角色、查/建角色——对话即操作
- 🔁 **手写 Agent 循环**：function calling 三段协议、并行工具调用、轮数熔断、流式输出
- 🧠 **会话记忆**：多轮上下文、超限截断、过期清理、归属校验
- 🛡 **五层注入防御**：提示词纪律 → 工具层权限闸 → admin 硬护栏 → 人工确认 → Service 层不变量
- 📡 **SSE 流式**：打字机输出、工具过程卡片、危险操作确认卡片
- ⚖️ **双实现对照**：同一套能力，手写与 Spring AI 各一份，差异一目了然

## 技术栈

Java 17 · Spring Boot 3.5 · MyBatis-Plus · MySQL 8 · Spring Security + JWT ·
Spring AI 1.1.x（OpenAI 兼容协议接入智谱 GLM）· SSE · Testcontainers · Docker Compose

## 模块地图

```
com.dking.mini_calling
├── controller / service / mapper / entity     # RBAC 业务（用户/角色/权限）
├── security                                   # JWT 过滤器、登录用户、权限加载
├── common                                     # Result 统一响应 / 全局异常 / 响应包装
├── config                                     # Security、CORS、MyBatis-Plus、OpenAPI
└── agent                                      # ─── AI 助手模块 ───
    ├── client                                 # LLM 客户端：OpenAI 兼容协议 DTO、
    │   │                                      #   流式解析（StreamCollector）、错误转译
    │   └── dto                                # ChatMessage / ChatRequest / ChatChunk ...
    ├── loop                                   # AgentLoop：手写 function calling 循环
    │   │                                      #   三个唯一出口：executeTool / runToolCalls / sinkOrSilent
    ├── tool                                   # 7 个管理工具 + 注册表 + 权限闸 + admin 硬护栏
    ├── memory                                 # 会话记忆（归属校验 / 截断 / TTL）
    ├── confirm                                # 危险操作挂起仓库（TTL / 一次性消费）
    ├── service                                # AgentService 编排：组装 → 循环 → 记忆
    ├── web                                    # 手写版端点（含 SSE、确认裁决）
    └── springai                               # Spring AI 框架版：@Tool 工具集 + ChatClient 装配 + 端点
```

## 快速开始

```bash
# 1. 准备数据库（结构 + 种子数据，含 admin 账号）
mysql -uroot -p < sql/init.sql

# 2. 配置智谱 API Key
#    application-local.yml 的 agent.api-key（手写版与 Spring AI 版共用）
#    或设置环境变量 ZHIPU_API_KEY

# 3. 启动
mvn spring-boot:run

# 4. 使用
#    Swagger：http://localhost:8080/swagger-ui/index.html
#    聊天页： http://localhost:8080/chat.html
#    登录 admin / admin123 后即可对话管理用户与角色
```

Docker 一键启动（app + mysql）：

```bash
ZHIPU_API_KEY=你的key docker compose up -d --build
```

## 学习路径（每个 Phase 一个 commit，可逐个 checkout 对照）

| Phase | 内容 | 关键类 |
|-------|------|--------|
| 0 | LLM API 对接：消息协议、流式、错误转译 | `client/LlmClient` |
| 1 | 多轮会话记忆、归属校验 | `memory/ChatMemoryStore` |
| 2 | **手写 function calling 循环**（三段协议 + 三条铁律） | `loop/AgentLoop` |
| 3 | 7 个真实管理工具、权限闸、admin 护栏 | `tool/*` |
| 4 | SSE 流式输出、事件协议、Reactor 入门 | `client/StreamCollector` |
| 5 | 危险操作挂起-裁决-恢复、五层注入防御、Service 层不变量 | `confirm/PendingActionStore` |
| 5.1/5.3 | 两轮重构：确认闸单点化、Null Object、组合优于平铺 | — |
| 6 | Spring AI 框架版对照实现 | `springai/*` |

## 手写版 vs Spring AI 框架版（实测对比）

| 能力 | 手写版（AgentLoop 一族） | Spring AI 版（ChatClient 一族） |
|------|------------------------|-------------------------------|
| HTTP/SSE 协议、DTO、分片聚合 | 手写（LlmClient + StreamCollector，~500 行） | 框架全包 ✅ |
| 工具 JSON Schema | 手写 Map | `@Tool` 注解自动生成 ✅ |
| tool_calls 循环 | 手写（铁律①③） | 框架内建 ✅ |
| 会话记忆 | 手写（归属校验/TTL/孤儿裁剪） | MessageWindowChatMemory 🔶 |
| 工具层权限、admin 护栏 | 自建 | 同样自建（语义逐条对齐）➖ |
| **危险操作人工确认** | **有（挂起-裁决-恢复状态机）** | **❌ 无，危险操作直接执行** |
| 工具过程事件（🔧 卡片） | 有 | ❌ 仅正文 delta |
| glm-4.6 思考模式关闭 | 支持（非标参数直传） | ❌ 无法透传，首字延迟更高 |

**结论**：框架消灭的是"协议翻译"层的重复劳动；确认状态机、安全护栏这类**业务决策**
框架只留插口不给实现——而后者才是一个 agent 系统的核心价值。

## 智谱 GLM 差异速查（相对 OpenAI 规范）

- 流式开关由请求体 `stream:true` 决定，`Accept` 头只是偏好表达
- `finish_reason` 私有枚举 `sensitive`（内容安全拦截，HTTP 仍 200）
- 工具调用 `id` 偶发为空，需本地补占位；`arguments` 是 JSON 字符串，需自行解析容错
- 流式末尾只含 usage 的分片 `choices` 为空数组，解析需空值防护
- 思考模型（glm-4.6 默认开启）：`thinking.type` 可关闭（非标参数），流式下返回
  `reasoning_content` 字段，DTO 必须忽略未知字段

## 安全模型（五层，对 LLM 逐层递减信任）

1. **提示词纪律**——行为引导，可被绕过
2. **工具层权限闸**——`requiredPermission` 比对调用者 authorities
3. **admin 硬护栏**——禁用/删除/改角色一票否决
4. **人工确认闸口**——危险操作挂起等裁决，拒绝不级联、独立裁决
5. **Service 层不变量**——所有入口（REST/agent/未来任何入口）共享的最后一道闸

真实漏洞案例：admin 可通过 REST 自删（权限码检查通过但对象级检查缺失）→
已在 Service 层修复并有测试锁定。

## 已知边界与改进方向

- SSE 超时后不中止进行中的 LLM 循环（会浪费 token）
- 无限流（内网管理工具 + JWT 门槛下可接受，公网前必须加限流与会话配额）
- 会话记忆在内存中，重启即失（持久化留作练习）
- Spring AI 版无确认机制（见对比表——这正是两个版本并存的意义）

## 测试

```bash
mvn test    # 67 个用例：客户端协议、循环铁律、记忆裁剪、工具集成（真实库）、
            # 确认裁决、Service 护栏、框架版工具安全语义
```

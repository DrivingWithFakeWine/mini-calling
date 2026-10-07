# Lessons

## 2026-10-05: 字段呈子集关系的两个类型，优先组合而不是平铺复制

- **现象**：PendingAction 把 PendingConfirmation 的五个字段逐个复制了一份，用户一眼看出"前者是后者的超集，为什么不直接组合"。
- **根源**：record 是增量演化的（baseCount 挪过家、remainingCalls 后加），每步单看都合理，但没回头审"字段子集关系"。平铺的代价是散弹式修改：挂起意图加字段，三处跟着改。
- **规则**：新增 record 时，检查它与已有 record 是否存在字段子集关系；是组成部分就用组合（`PendingAction = 挂起意图 + 仓库元数据`），并让"提供数据的那一层"的类型作为被组合方。重构成本几乎为零时（record + 构造器传参），别以"字段一目了然"为借口留着平铺。

## 2026-09-29: Claude Code 的 /agents 管理向导已被移除

- **现象**：告诉用户输入 `/agents` 可以打开 subagent 管理界面，但用户的新版 Claude Code 返回 "The /agents wizard has been removed"。
- **教训**：Claude Code 功能迭代很快，不要凭旧知识断言具体的 UI/命令行为。涉及产品功能细节时，先查最新官方文档（code.claude.com/docs）或让用户实际验证。
- **现行规则**：subagent 的创建/修改已改为对话式（直接让 Claude 帮你读写 `.claude/agents/*.md`）或手动编辑文件；项目级在 `.claude/agents/`，用户级在 `~/.claude/agents/`。

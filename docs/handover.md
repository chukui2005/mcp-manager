# 项目换届与会话交接指南 (Handover Guide)

> **当前维护者**: MCP Governance 统筹中枢  
> **更新时间**: 2026-10-29  
> **当前版本**: v0.1.0-alpha  
> **对应阶段**: Phase 1 (静态扫描 CLI 与 GitHub Action)

---

## 1. 会话轮转与换届纪律 (Session Rotation Rule)

根据《AGENTS.md》宪章第 3.1 节，为了防止会话上下文过长导致模型“降智”（注意力衰减、规则遗忘）以及响应延迟翻倍，本项目执行严格的**会话轮转制度**：

### 换届标准触发点（何时精准新开会话）：
- **精确触发条件**：**当且仅当一个完整的 TASK 闭环合并到 `main` 分支后（状态机达到 `DONE`）**。
- **换届操作**：
  1. 当前统筹者在合入 `main` 后，更新本文档（`docs/handover.md`）中的当前进展与下一步目标；
  2. 人类在 DeepSeek Harness Web 界面点击 **“新建会话”**；
  3. 人类将本文档第 4 节的【开场接力指令】一键复制发送给新会话 AI；
  4. 新会话 AI 拥有 100% 崭新、纯净的 Token 窗口，通过读取物理文件瞬间接力，继续调用智能体团队推进下一个 TASK。

---

## 2. 当前工程与资产完成现状 (Status)

### 2.1 已完成并合并入 `main` 的里程碑
1. **基础设施与协作宪章**:
   - `AGENTS.md`：确立三权分立（架构师只读、工程师隔离分支、审核员只读把关、人类最终批准）与状态机规范；
   - 语言规范：强制全中文文档与注释规范；
   - 远程绑定：`https://github.com/chukui2005/mcp-manager.git`，GroupId `io.github.chukui2005`。
2. **`TASK-000-dry-run` 闭环演练**:
   - 验证了 ADR 0000 产出、`Approved: Yes` 人类门禁、隔离分支提交、Reviewer 判定与人类合并全流程。
3. **`TASK-001` 多模块骨架与 SARIF 契约**:
   - **架构设计**: 产出中文版 [docs/adr/0001-project-scope-and-modules.md](docs/adr/0001-project-scope-and-modules.md)；
   - **契约基线**: 产出 OASIS SARIF 2.1.0 契约 [docs/contracts/scan-result.schema.json](docs/contracts/scan-result.schema.json)；
   - **核心调研**: 架构师通过学习 Sprint 产出权威研读笔记 [docs/learning-notes.md](docs/learning-notes.md)；
   - **代码落地**: 
     - `mcp-governance-core`: 纯 Java 17，零 Spring 依赖，实现防篡改 SHA-256 签名的不可变 `GovernedTool` 与 `ScanRule` SPI；
     - `mcp-governance-scanner`: 静态评估引擎与 SARIF 2.1.0 序列化器；
     - `mcp-governance-rules-owasp`: 实现 MCP01-001 明文密钥检测规则（支持 Secret 脱敏与环境变量语法放行）；
     - `mcp-governance-cli`: 基于 Picocli 的命令行工具，测试覆盖阻断退出码；
   - **质量门禁**: 10/10 单元测试全部通过，Javadoc 编译零告警。

---

## 3. 下一个任务目标：TASK-002

### 任务名称：真实 MCP 配置文件解析引擎与 OWASP MCP02/MCP03 规则集

- **模块重点**: `mcp-governance-scanner` 与 `mcp-governance-rules-owasp`
- **核心需求**:
  1. **配置解析器 (Parser)**: 支持静态解析真实的 `mcp.json` 与 Claude Desktop 格式 (`claude_desktop_config.json`)，支持提取 `mcpServers` 中的环境变量、参数、命令定义；
  2. **MCP02 规则 (权限蔓延)**:
     - 规则 ID: `MCP02-001` (过度宽泛的工具权限定义，如包含通配符 `*` 或具有直接破坏性的未授权系统操作)；
  3. **MCP03 规则 (工具投毒与提示注入)**:
     - 规则 ID: `MCP03-001` (工具描述中的隐藏指令与提示词注入检测，如 `Ignore previous instructions`、`<script>` 注入特征检测)；
     - 规则 ID: `MCP03-002` (工具影子混淆/重名覆盖风险检测 Tool Shadowing)；
  4. **CLI 输出体验优化**: 控制台增加 ANSI 彩色 Markdown 格式的风险摘要输出。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层，Phase 1）。
目前 TASK-000 与 TASK-001（Maven 4 模块架构骨架与 SARIF 契约）已经全部研发完成并合入 main 分支。
请你严格阅读仓库根目录的以下核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0001-project-scope-and-modules.md（模块架构 ADR）
5. docs/learning-notes.md（MCP 与 OWASP 规范调研成果）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-002：实现真实的 MCP 配置文件解析器与 OWASP MCP02/MCP03 规则集。
请分派架构师队友产出 ADR 0002 与设计蓝图，待我签署 Approved: Yes 后推进。所有文档、注释一律使用中文。
```

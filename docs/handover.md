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
   - 确立 ADR 0001 与 SARIF 2.1.0 契约；
   - 实现纯 Java 领域模型 `GovernedTool`、静态引擎与 `MCP01-001` 密钥检测；
4. **`TASK-002` 真实 MCP 配置文件解析引擎与 OWASP MCP02/MCP03 规则集 (最新完成)**:
   - **架构设计**: 产出中文版 [docs/adr/0002-mcp-config-parser-and-owasp-rules.md](docs/adr/0002-mcp-config-parser-and-owasp-rules.md) 与 [docs/architecture/0002-blueprint.md](docs/architecture/0002-blueprint.md)；
   - **领域模型**: `mcp-governance-core` 引入不可变 `McpConfig`、`McpServerConfig` 与扩展 `ScanContext`，100% 保持零 Spring 依赖；
   - **配置解析**: `mcp-governance-scanner` 实现 `McpConfigParser`，纯静态解析 `mcp.json` 与 Claude Desktop 格式，防范外部命令执行；
   - **OWASP 规则**: `mcp-governance-rules-owasp` 实现并注册 `MCP02-001` (权限蔓延/通配符)、`MCP03-001` (隐藏提示注入)、`MCP03-002` (同名工具影子劫持)；
   - **CLI 终端**: `mcp-governance-cli` 实现 ANSI 彩色 Markdown 报告渲染器 `ConsoleReportRenderer` 与 Exit Code 2 门禁；
   - **全量门禁**: 全模块 19 个单元测试全部通过，`mvn javadoc:javadoc` 零警告，已合入 `main` 分支。

---

## 3. 下一个任务目标：TASK-003 (Phase 1 收官交付)

### 任务名称：GitHub Action 自举静态审计与 Phase 1 双语 README 交付

- **模块重点**: `mcp-governance-action` 与根目录 README 体系
- **核心需求**:
  1. **GitHub Action 封装 (`mcp-governance-action`)**:
     - 编写 Action 清单（`action.yml`）与 Dockerfile / Composite 脚本，支持在 GitHub CI 中一键运行 `mcp-governance-cli`；
     - 自动导出符合 OASIS SARIF 2.1.0 规范的 `scan-result.sarif` 报表；
     - 集成 `github/codeql-action/upload-sarif@v3` 实现代码扫描内联告警。
  2. **项目级 SARIF 自举扫描**:
     - 为本仓库自身添加 `.github/workflows/mcp-governance-scan.yml`，在 PR/Push 时静态审计项目自测示例配置，验证零 Critical 门禁。
  3. **双语 README 交付 (Phase 1 结项标志)**:
     - 按照《AGENTS.md》第 8 节要求撰写中英双语文档：`README.md` (英文) 与 `README.zh-CN.md` (中文)；
     - 首屏包含：一句话定位、真实终端彩色输出截图/预览、同类对比表、5 秒上手 Quick Start、架构图与 Phase 1 Roadmap；
     - 首屏严格中英互链。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层，Phase 1）。
目前 TASK-000、TASK-001 与 TASK-002（真实配置解析器与 OWASP MCP01/02/03 规则集、ANSI 彩色报告）已经全部研发完成并合入 main 分支。
请你严格阅读仓库根目录的以下核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0002-mcp-config-parser-and-owasp-rules.md（最新配置与规则 ADR）
5. docs/contracts/scan-result.schema.json（SARIF 契约规范）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-003（Phase 1 收官任务）：GitHub Action 自举静态审计与 Phase 1 双语 README 交付。
请分派架构师队友产出 ADR 0003 与设计蓝图，待我签署 Approved: Yes 后推进。所有文档、注释一律使用中文。
```

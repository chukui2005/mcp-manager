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
4. **`TASK-002` 真实 MCP 配置文件解析引擎与 OWASP MCP02/MCP03 规则集**:
   - 实现 `McpConfigParser` 与不可变 `McpConfig` 模型；
   - 实现 `MCP02-001` (权限蔓延/通配符)、`MCP03-001` (隐藏提示注入)、`MCP03-002` (同名工具影子劫持)；
   - 实现 ANSI 彩色 Markdown 报告渲染器 `ConsoleReportRenderer` 与 Exit Code 2 门禁。
5. **`TASK-003` GitHub Action 自举静态审计与 Phase 1 双语 README 交付 (Phase 1 结项收官)**:
   - **架构设计**: 产出中文版 [docs/adr/0003-github-action-and-readme-deliverables.md](docs/adr/0003-github-action-and-readme-deliverables.md) 与 [docs/architecture/0003-blueprint.md](docs/architecture/0003-blueprint.md)；
   - **CLI 可执行包**: `mcp-governance-cli` 配置 `maven-shade-plugin` 与 `ServicesResourceTransformer`，确保规则 SPI 安全合并，生成 `mcp-governance-cli-*-all.jar`；
   - **Action 封装**: 根目录与模块提供 Composite Action (`action.yml`)，支持在 GitHub CI 中一键运行审计，产出标准 SARIF 2.1.0 报表，集成 `github/codeql-action/upload-sarif@v3` 实现代码扫描内联告警；
   - **CI 自举审计**: `.github/workflows/mcp-governance-scan.yml` 结合 `samples/clean-mcp.json` 与 `samples/vulnerable-mcp.json` 完成正向通过与对抗阻断 (Exit Code 2) 校验；
   - **双语开源门面**: 严格依据《AGENTS.md》第 8 节排版结构交付全英文 [README.md](README.md) 与全中文 [README.zh-CN.md](README.zh-CN.md)，首屏互链、高保真 ANSI 彩色效果预览、同类产品对比矩阵与真实 Roadmap；
   - **全量门禁**: 全模块 19 个单元测试 100% 通过，`mvn javadoc:javadoc` 零警告，Reviewer 审查给出 `verdict: PASS`。

---

## 3. 下一个任务目标：TASK-004 (Phase 2 启航任务)

### 任务名称：mcp-governance-spring-boot-starter 模块脚手架与运行时请求拦截契约设计

- **所属阶段**: Phase 2 (运行时治理与 Spring Boot Starter)
- **模块重点**: `mcp-governance-spring-boot-starter` 与 `mcp-governance-core` 运行时扩展
- **核心需求**:
  1. **Starter 模块架构设计**:
     - 依照《AGENTS.md》第 5.1 节与 6.1 节，产出 ADR 0004 与设计蓝图；
     - 引入 `@ConditionalOnClass` / `@ConditionalOnMissingBean` 条件装配，严禁强制引入非必要依赖；
     - 配置属性通过 `@ConfigurationProperties(prefix = "mcp.governance")` 绑定并具备完备 Javadoc 与安全默认值。
  2. **运行时控制面拦截模型**:
     - 基于不可变领域模型，设计运行时 MCP 请求审计拦截器接口与日志上下文；
     - 隔离底层 Wire 传输，绝不泄漏 SDK 内部原始类型。
  3. **单元测试与条件装配验证**:
     - 编写上下文加载测试，验证在缺少相关依赖时的平滑降级与非侵入性。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层）。
Phase 1（静态扫描 CLI、OWASP 规则集、GitHub Action 与双语 README 门面）已全部收官并合入 main 分支。
请你严格阅读仓库根目录的以下核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0003-github-action-and-readme-deliverables.md（Phase 1 收官 ADR）
5. docs/contracts/scan-result.schema.json（SARIF 契约规范）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-004（Phase 2 启航任务）：mcp-governance-spring-boot-starter 模块脚手架与运行时请求拦截契约设计。
请分派架构师队友产出 ADR 0004 与设计蓝图，待我签署 Approved: Yes 后推进。所有文档、注释一律使用中文。
```

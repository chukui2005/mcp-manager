# 项目换届与会话交接指南 (Handover Guide)

> **当前维护者**: MCP Governance 统筹中枢  
> **更新时间**: 2026-10-29  
> **当前版本**: v0.2.0-alpha  
> **对应阶段**: Phase 2 (运行时治理与 Spring Boot Starter)

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
   - 产出 ADR 0003 与 Blueprint 0003，实现 Shade Fat-JAR、Composite Action 封装与 CI 自举扫描；
   - 交付中英双语 `README.md` 与 `README.zh-CN.md`。
6. **`TASK-004` Spring Boot Starter 脚手架与运行时拦截契约设计 (Phase 2 启航收官)**:
   - **架构决策与设计**: 产出 [docs/adr/0004-spring-boot-starter-and-runtime-interception.md](docs/adr/0004-spring-boot-starter-and-runtime-interception.md) 与 [docs/architecture/0004-blueprint.md](docs/architecture/0004-blueprint.md)；
   - **纯 Java 核心拦截契约 (`mcp-governance-core`)**: 实现不可变上下文 `McpInvocationContext`、调用结果 `McpInvocationResult`、拦截器接口 `McpInterceptor`、责任链执行器 `McpInterceptorChain` 与治理异常体系，坚守零 Spring 依赖红线，严格隔离 Wire 传输模型；
   - **Starter 自动装配 (`mcp-governance-spring-boot-starter`)**: 基于 Spring Boot 3.3.4，引入 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 条件装配，提供开箱即用的敏感参数掩码脱敏与调用审计拦截器 `LoggingMcpInterceptor`；
   - **全量门禁**: 全工程 6 个模块、40 个单元测试 100% PASS，`mvn javadoc:javadoc` 零警告，审查给出 `verdict: PASS`，已成功合入 `main`。

---

## 3. 下一个任务目标：TASK-005 (运行时策略拦截器实现与 OWASP MCP02 动态治理)

### 任务名称：动态运行时策略拦截器集实现与 Spring Boot 深度集成

- **所属阶段**: Phase 2 (运行时治理与 Spring Boot Starter)
- **模块重点**: `mcp-governance-spring-boot-starter` 与 `mcp-governance-core`
- **核心需求**:
  1. **运行时访问控制与限流/权限拦截器**:
     - 基于不可变调用上下文 `McpInvocationContext`，实现针对工具调用权限（Tool ACL / 白名单 / 动态黑名单）的拦截器（映射 OWASP MCP02 权限蔓延治理）；
     - 实现动态参数规则校验拦截器（防提示注入与越权入参）；
  2. **端到端适配与集成演示**:
     - 提供简易测试 Mock 服务或 MCP 客户端适配器示例，展示如何在 Controller/Service 运行时切面调用 `McpInterceptorChain` 实现端到端鉴权与审计；
  3. **自动化测试与门禁**:
     - 编写多场景拦截与阻断集成测试，确保 Fail-Fast 生效与脱敏报表输出。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层）。
Phase 1（静态扫描与 GitHub Action）与 TASK-004（Spring Boot Starter 模块脚手架与运行时请求拦截契约设计）已全部收官并合入 main 分支。
请你严格阅读仓库根目录的以下核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0004-spring-boot-starter-and-runtime-interception.md（运行时契约 ADR）
5. docs/architecture/0004-blueprint.md（运行时实施蓝图）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-005：动态运行时策略拦截器集实现与 Spring Boot 深度集成（Tool ACL 白名单治理与动态策略执行）。
请分派架构师队友产出 ADR 0005 与设计蓝图，待我签署 Approved: Yes 后推进。所有文档、注释一律使用中文。
```

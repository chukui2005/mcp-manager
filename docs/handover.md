# 项目换届与会话交接指南 (Handover Guide)

> **当前维护者**: MCP Governance 统筹中枢  
> **更新时间**: 2026-10-31  
> **当前版本**: v0.2.0-beta (Phase 2 全面收官)  
> **对应阶段**: Phase 2 (运行时治理与 Spring Boot Starter 收官) -> Phase 3 准备

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
   - 产出 ADR 0004 与 Blueprint 0004，建立纯 Java 不可变上下文模型与责任链拦截器契约；
   - 实现 Spring Boot 3.3.4 条件装配与敏感入参脱敏审计拦截器 `LoggingMcpInterceptor`。
7. **`TASK-005` 运行时策略拦截器集实现与 Spring Boot 深度集成 (Phase 2 核心收官)**:
   - **架构决策与蓝图**: 产出 [docs/adr/0005-runtime-policy-and-tool-acl.md](docs/adr/0005-runtime-policy-and-tool-acl.md)、[docs/architecture/0005-blueprint.md](docs/architecture/0005-blueprint.md) 及 [docs/contracts/runtime-policy.schema.json](docs/contracts/runtime-policy.schema.json)；
   - **纯 Java 策略模型与拦截器 (`mcp-governance-core`)**: 实现 `AclMode`（WHITELIST/BLACKLIST）、`ToolAclPolicy`（支持通配符 `file_*`、`db:*` 与正则缓存）、`ParameterValidationRule`、`ToolAclMcpInterceptor` (Order: 100，OWASP MCP02 治理) 与 `ParameterValidationMcpInterceptor` (Order: 200，防越权入参与路径遍历正则拦截)；
   - **Starter 自动装配与端到端适配 (`mcp-governance-spring-boot-starter`)**: 扩展 `McpGovernanceProperties` 支持 `acl` 与 `validation` 嵌套属性绑定，通过 `McpGovernanceAutoConfiguration` 条件注入责任链，提供 `McpGovernanceAdapter` 端到端调用拦截模板；
   - **全量门禁**: 全工程 6 个模块、54 个单元/集成测试 100% PASS，`mvn javadoc:javadoc` 零警告，审查给出 `verdict: PASS`，已成功合入 `main`。

8. **`TASK-006` 运行时指标遥测与 Micrometer / Actuator 监控观测集成 (Phase 2 可观测性收官)**:
   - **架构决策与蓝图**: 产出 [docs/adr/0006-runtime-metrics-and-actuator.md](docs/adr/0006-runtime-metrics-and-actuator.md)、[docs/architecture/0006-blueprint.md](docs/architecture/0006-blueprint.md) 及 [docs/contracts/metrics-snapshot.schema.json](docs/contracts/metrics-snapshot.schema.json)；
   - **纯 Java 核心遥测抽象 (`mcp-governance-core`)**: 实现 `InvocationStatus`、`ToolMetricsSnapshot`、`McpMetricsSnapshot`、`McpMetricsCollector` SPI，以及基于 `LongAdder` 的无锁并发指标收集器 `InMemoryMcpMetricsCollector`；
   - **最外层拦截器编排**: 实现 `MetricsMcpInterceptor` (Order: -100)，处于责任链最外围，100% 覆盖并捕获成功调用、Tool ACL 阻断 (OWASP MCP02) 与参数校验违规 (OWASP MCP05)，杜绝监控逃逸；
   - **Starter 自动装配与优雅降级 (`mcp-governance-spring-boot-starter`)**: 声明可选依赖，实现 `MicrometerMcpMetricsCollector` 与 Spring Boot Actuator 端点 `McpGovernanceEndpoint`（`/actuator/mcpGovernance`）；
   - **全量门禁**: 全工程 6 个模块、69 个单元/集成测试 100% PASS，`mvn javadoc:javadoc` 零警告，审查给出 `verdict: PASS`。

9. **`TASK-007` 运行时审计记录结构化导出与事件监听器体系 (Phase 2 全面圆满收官)**:
   - **架构决策与蓝图**: 产出 [docs/adr/0007-runtime-audit-event-and-listener.md](docs/adr/0007-runtime-audit-event-and-listener.md)、[docs/architecture/0007-blueprint.md](docs/architecture/0007-blueprint.md) 及 [docs/contracts/audit-event.schema.json](docs/contracts/audit-event.schema.json)；
   - **纯 Java 审计事件与发布监听体系 (`mcp-governance-core`)**: 实现不可变结构化记录 `McpAuditEvent`、深度递归敏感参数打码工具 `SensitiveDataMasker`（防 OWASP MCP01）、`McpAuditPublisher` 与 `McpAuditListener` SPI 接口，以及内置异常隔离沙箱的 `DefaultMcpAuditPublisher`；
   - **全生命周期审计拦截器**: 实现 `AuditMcpInterceptor` (Order: -50)，处于最外围第二层，紧邻 Metrics (-100)，全生命周期无死角捕获正常执行与 Tool ACL 阻断、参数校验失败（防 OWASP MCP08 审计逃逸）；
   - **Spring Boot 事件机制与结构化导出 (`mcp-governance-spring-boot-starter`)**: 实现 `SpringMcpAuditEvent` 与 `SpringMcpAuditPublisher`（支持原生 `@EventListener` 异步解耦），以及使用 Jackson 导出单行紧凑 JSON 格式的 `JsonLoggingMcpAuditListener`（原生适配 SIEM/ELK 免正则采集）；
   - **全量门禁**: 全工程 6 个模块、84 个单元/集成测试 100% PASS，`mvn javadoc:javadoc` 零警告，审查给出 `verdict: PASS`，已成功合并至 `main`。

---

## 3. 下一个任务目标：TASK-008 (Phase 2 整体发布回顾与 Phase 3 独立网关架构预研 / Starter 易用性增强)

### 任务名称：Phase 2 结项复盘与独立网关 (Gateway) 架构设计启动 (TASK-008)
- **所属阶段**: Phase 2 收官验收 / Phase 3 (独立 MCP 网关架构)
- **模块重点**: 架构设计与整体集成文档
- **核心需求**:
  1. **Phase 2 成果封版验收与文档同步**: 完善 `README.md` 与 `README.zh-CN.md` 中的 Phase 2 Starter 运行时治理功能矩阵（拦截器、Tool ACL、参数校验、指标遥测、审计事件）；
  2. **Phase 3 架构愿景启动**: 针对独立 MCP 网关（Phase 3 Gateway）编写前瞻架构决策与分层契约（HTTP/SSE/STDIO 协议代理治理模型）。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层）。
Phase 1 与 Phase 2（TASK-004、TASK-005、TASK-006、TASK-007）全部圆满收官并合入 main 分支，包含了运行时拦截契约、Tool ACL 权限治理、指标遥测与 Actuator 端点、结构化审计事件与监听器体系。
请你严格阅读仓库根目录的以下核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0007-runtime-audit-event-and-listener.md（运行时审计 ADR）
5. docs/architecture/0007-blueprint.md（运行时审计蓝图）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-008：Phase 2 结项验收复盘与 Phase 3 独立网关架构预研。
请分派架构师队友产出相关规划与设计蓝图，待我签署 Approved: Yes 后推进。所有文档、注释一律使用中文。
```

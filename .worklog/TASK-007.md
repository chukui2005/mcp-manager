# TASK-007: 运行时审计记录结构化导出与事件监听器体系

> **所属阶段**: Phase 2 (运行时治理与 Spring Boot Starter)  
> **任务负责人**: architect (架构师) -> engineer (工程师) -> reviewer (审核员) -> 人类 (合并)  
> **状态**: IN_REVIEW  

---

## 1. 任务背景与核心目标

在完成 TASK-004（拦截契约与脱敏审计日志）、TASK-005（Tool ACL 与参数防越权注入）与 TASK-006（运行时指标遥测与 Actuator 观测端点）后，运行时体系已经具备完备的控制面防护与指标大盘。
为了彻底闭环 Phase 2 并满足企业级安全合规及 **OWASP MCP01（凭据泄露防范）/ MCP08（审计与遥测缺失）**，必须建立标准化的**运行时审计记录结构化导出与事件发布/监听体系**：

### 核心交付物：
1. **纯 Java 审计事件与发布监听抽象 (`mcp-governance-core`)**：
   - 保持 `core` 纯 Java 17，无 Spring 强依赖；
   - 定义不可变的结构化审计记录模型 `McpAuditEvent`（包含追踪 ID、客户端/主体信息、工具名、脱敏入参摘要、执行状态、耗时、异常及策略阻断信息、时间戳）；
   - 提供 `McpAuditPublisher` 与 `McpAuditListener` SPI 契约；
   - 提供内存/控制台默认实现与审计拦截机制。
2. **Spring Boot 事件机制与结构化导出集成 (`mcp-governance-spring-boot-starter`)**：
   - 将审计事件无缝桥接至 Spring `ApplicationEventPublisher`（发布 `SpringMcpAuditEvent`），支持应用层使用 `@EventListener` 异步消费；
   - 提供结构化日志导出（JSON 格式或结构化文本，支持 Logback/SLF4J 归档与外部 SIEM/ES 收集）；
   - 支持条件配置开启、异步派发与优雅降级。
3. **架构设计与文档**：
   - `docs/adr/0007-runtime-audit-event-and-listener.md`；
   - `docs/architecture/0007-blueprint.md`；
   - `docs/contracts/audit-event.schema.json`。

---

## 2. 状态机流转

- [x] **DESIGN** (架构师编写 ADR 与实施蓝图，等待人类签署 Approved: Yes)
- [x] **READY** (人类签署 Approved: Yes，工程师就绪隔离分支开发)
- [x] **IN_REVIEW** (工程师完成实现、单测与 CI，提交 PR 并请求审查)
- [ ] **READY_TO_LAND** (审核员审查通过，给出 verdict: PASS)
- [ ] **DONE** (人类确认合并到 main 分支)

---

## 3. 人类审批记录

Approved: Yes
审批时间: 2026-10-31
审批意见: 方案规范详尽，同意推进实现。

---

## 4. 工程师实现交付摘要 (Implementation Summary)

- **实现分支**: `feature/task-007-runtime-audit`
- **交付内容**:
  1. `mcp-governance-core`（纯 Java 17，零 Spring 依赖）：
     - `SensitiveDataMasker`: 敏感数据深度递归脱敏工具，支持嵌套 Map/List 中 password/secret/token/apikey/authorization 等关键词打码为 `******`；
     - `McpAuditEvent`: 不可变结构化审计事件记录（Record），满足契约 `audit-event.schema.json` 与 OWASP MCP01/MCP08 规范；
     - `McpAuditListener`: 函数式审计事件监听器契约；
     - `McpAuditPublisher`: 审计事件发布器契约；
     - `DefaultMcpAuditPublisher`: 并发安全的纯 Java 事件广播器，内置异常沙箱隔离机制；
     - `AuditMcpInterceptor`: 核心审计拦截器（`getOrder() = -50`），覆盖正常调用、Tool ACL 阻断、参数校验违规全生命周期。
  2. `mcp-governance-spring-boot-starter`：
     - `SpringMcpAuditEvent`: 继承 `ApplicationEvent`，桥接 Spring 原生事件模型；
     - `SpringMcpAuditPublisher`: 结合纯 Java 监听器与 Spring `ApplicationEventPublisher`；
     - `JsonLoggingMcpAuditListener`: 使用 Jackson 导出单行紧凑 JSON 格式审计日志（目标 Logger 默认 `io.github.chukui2005.mcpgov.audit`）；
     - `McpGovernanceProperties`: 增加 `AuditProperties.JsonExportProperties` 与 `SpringEventProperties` 嵌套配置；
     - `McpGovernanceAutoConfiguration`: 自动装配发布器、监听器与拦截器，支持 `@ConditionalOnProperty` / `@ConditionalOnMissingBean`。
  3. 质量与验证结果：
     - 包含脱敏、沙箱广播、拦截器全生命周期、JSON 单行导出以及 Spring 原生 `@EventListener` 端到端集成测试；
     - 全量验证 `mvn -B clean verify` 全部通过（共 84 个测试用例，0 失败，0 错误）；
     - `mvn javadoc:javadoc` 零警告，所有公共 API 均提供规范中文 Javadoc。

---

## 5. 审核员审查意见 (Reviewer Findings)

*(待审查阶段填写)*

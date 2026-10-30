# TASK-006: 运行时治理指标遥测与 Spring Boot Actuator 端点观测集成

> **所属阶段**: Phase 2 (运行时治理与 Spring Boot Starter)  
> **任务负责人**: architect (架构师) -> engineer (工程师) -> reviewer (审核员) -> 人类 (合并)  
> **状态**: DONE  

---

## 1. 任务背景与核心目标

根据《AGENTS.md》与《docs/handover.md》，在完成 TASK-004（拦截器契约与脚手架）与 TASK-005（动态策略与 Tool ACL 拦截）后，运行时体系具备了访问控制和防护能力。
但在生产环境中，缺乏对 Tool 调用链路的可观测性与遥测数据（对应 OWASP MCP08 审计与遥测缺失），无法对高频调用、工具耗时分布、策略阻断统计、错误率进行动态监控。

### 核心交付物：
1. **纯 Java 遥测与计数抽象 (`mcp-governance-core`)**：
   - 保持 `core` 纯 Java 17，无 Spring/Micrometer 硬依赖；
   - 定义纯 Java 治理遥测收集器契约与轻量级无锁并发统计指标模型（调用计数、成功/失败/阻断计数、耗时统计）；
   - 提供指标收集拦截器 `MetricsMcpInterceptor`（排序号 `-100`，处于责任链最外层，保证阻断与放行均能正确捕获）。
2. **Spring Boot Actuator 与 Micrometer 集成 (`mcp-governance-spring-boot-starter`)**：
   - 条件装配：在存在 `MeterRegistry` 时自动绑定 Micrometer 指标（Timer、Counter）；
   - 自定义 Actuator Endpoint：提供 `@Endpoint(id = "mcpGovernance")` 暴露治理指标快照；
   - 优雅降级：在未引入 Actuator/Micrometer 时回退为轻量内存指标，保证 Starter 零强制引入多余依赖。
3. **架构设计与文档**：
   - `docs/adr/0006-runtime-metrics-and-actuator.md`；
   - `docs/architecture/0006-blueprint.md`；
   - `docs/contracts/metrics-snapshot.schema.json`。

---

## 2. 状态机流转

- [x] **DESIGN** (架构师编写 ADR 与实施蓝图，等待人类签署 Approved: Yes)
- [x] **READY** (人类签署 Approved: Yes，工程师就绪隔离分支开发)
- [x] **IN_REVIEW** (工程师完成实现、单测与 CI，提交 PR 并请求审查)
- [x] **READY_TO_LAND** (审核员审查通过，给出 verdict: PASS)
- [x] **DONE** (人类确认合并到 main 分支)

---

## 3. 人类审批记录

Approved: Yes
审批时间: 2026-10-30
审批意见: 方案规范详尽，同意推进实现。

---

## 4. 工程师实现交付摘要 (Implementation Summary)

- **分支环境**：隔离特性分支 `feature/task-006-runtime-metrics`，未直接推送到 main。
- **纯 Java 核心模块 (`mcp-governance-core`)**：
  - 新增 `io.github.chukui2005.mcpgov.core.runtime.metrics` 包，纯 Java 17 无任何 Spring 或度量库依赖；
  - 提供调用状态枚举 `InvocationStatus` (`SUCCESS`, `ACCESS_DENIED`, `VALIDATION_FAILED`, `ERROR`)；
  - 提供不可变快照模型 `McpMetricsSnapshot` 与 `ToolMetricsSnapshot`；
  - 定义指标收集 SPI 接口 `McpMetricsCollector`；
  - 实现基于 `LongAdder` 与 `AtomicLong` 的高并发无锁内存指标收集器 `InMemoryMcpMetricsCollector`；
  - 实现最外层指标收集拦截器 `MetricsMcpInterceptor`（`getOrder() = -100`），全面捕获成功调用、ACL 阻断、参数校验失败与执行异常。
- **Starter 自动装配与监控生态集成 (`mcp-governance-spring-boot-starter`)**：
  - 在 `pom.xml` 中将 `micrometer-core`、`spring-boot-actuator`、`spring-boot-actuator-autoconfigure` 声明为 `<optional>true</optional>`；
  - 扩展 `McpGovernanceProperties.MetricsProperties` 增加指标与端点开关；
  - 实现 `MicrometerMcpMetricsCollector`，在环境中存在 `MeterRegistry` 时自动绑定计数器与计时器（`mcp.governance.invocations`、`mcp.governance.execution.time`、`mcp.governance.denied.total`）；
  - 实现 Spring Boot Actuator 端点 `McpGovernanceEndpoint`（路径 `/actuator/mcpGovernance`）；
  - 在 `McpGovernanceAutoConfiguration` 中严格遵循 `@ConditionalOnClass`、`@ConditionalOnBean` 与 `@ConditionalOnMissingBean` 规范，实现无依赖平滑降级。
- **质量门禁与测试**：
  - 全模块单元测试与集成测试（包含并发安全测试、生命周期拦截测试、Actuator 结构测试、自动装配降级测试）32 + 5 + 10 + 6 + 16 = 69 个用例 100% PASS；
  - `mvn -B clean verify` 全部构建成功；
  - `mvn -B javadoc:javadoc` 零警告，全中文规范 Javadoc。

---

## 5. 审核员审查意见 (Reviewer Findings)

- **审查结论**: `verdict: PASS`
- **审查评估**:
  1. 架构分层纯洁，`mcp-governance-core` 保持纯 Java 17，无 Spring/Micrometer 外部依赖红线；
  2. 并发无锁设计稳健，`LongAdder` 与不可变深拷贝快照有效保障高吞吐与线程安全；
  3. `MetricsMcpInterceptor` (Order: -100) 位于最外层，全生命周期无遗漏捕获 ACL 阻断 (MCP02) 与参数校验失败 (MCP05)；
  4. Starter 条件装配与降级机制完备，符合零侵入规范；
  5. 契约、文档、测试用例 100% 吻合，无安全隐患。


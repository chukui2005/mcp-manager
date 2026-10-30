# 0006. 运行时治理指标遥测与 Spring Boot Actuator 端点观测集成设计

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-30
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0004-spring-boot-starter-and-runtime-interception.md, docs/adr/0005-runtime-policy-and-tool-acl.md, .worklog/TASK-006.md

---

## 背景 (Context)

在 TASK-004 与 TASK-005 中，本项目建立了运行时拦截契约、敏感参数脱敏审计、Tool ACL 权限治理（OWASP MCP02）与参数安全校验能力。至此，MCP 治理层已具备坚实的运行时请求控制底座。

然而，面对企业多 Agent、多 Tool 的复杂生产交互环境，缺乏统一的可观测性与度量遥测体系，直接对应 **OWASP MCP08 审计与遥测缺失（Lack of Auditing & Telemetry）** 风险，具体痛点如下：
1. **黑盒化与盲区风险**：运维与安全管理员无法实时感知高频被调用的敏感 Tool、工具执行耗时分布（P50/P95/P99）、异常失败波动以及策略拦截阻断率。
2. **分层与无依赖约束（不可协商）**：
   - 根据《AGENTS.md》第 4 节：`mcp-governance-core` 必须保持**纯 Java 17，绝对禁止引入 Spring、Micrometer 或任何度量框架依赖**。
   - 控制面模型与 Wire 协议强隔离，指标收集抽象必须脱离具体通信协议（STDIO / SSE / HTTP）。
3. **监控生态集成与优雅降级**：
   - 在 `mcp-governance-spring-boot-starter` 中，企业微服务普遍采用 Spring Boot Actuator 与 Prometheus/Micrometer 技术栈。Starter 需提供开箱即用的自动绑定能力。
   - 同时，必须遵循条件装配原则（`@ConditionalOnClass`）。当宿主未引入 Micrometer 或 Actuator 时，必须平滑回退至轻量内存统计或空实现，**严禁引入硬依赖导致宿主发生 `ClassNotFoundException`**。
4. **全链路覆盖的拦截器编排**：
   - 遥测必须精准捕获：成功调用、被 Tool ACL 阻断调用（MCP02）、参数校验失败（MCP05）、底层工具抛出的业务或系统异常，确保阻断与失败不从监控视图中逃逸。

---

## 架构决策 (Decision)

### 1. 纯 Java 遥测领域模型与 SPI 抽象（置于 `mcp-governance-core`）

在 `io.github.chukui2005.mcpgov.core.runtime.metrics` 包下定义纯 Java 17 领域模型，保持零第三方依赖：

#### 1.1 执行结果状态与调用指标事件
- **`InvocationStatus` (枚举)**：
  - `SUCCESS`：工具成功执行并产出结果；
  - `ACCESS_DENIED`：被 Tool ACL 权限策略拒绝（OWASP MCP02）；
  - `VALIDATION_FAILED`：入参校验未通过（必填缺失、正则匹配违规等）；
  - `ERROR`：底层业务执行或未知系统异常。
- **`McpMetricsCollector` (指标收集器 SPI 接口)**：
  ```java
  public interface McpMetricsCollector {
      /**
       * 记录单次工具调用的治理与执行指标。
       *
       * @param toolName 工具名称
       * @param durationMs 执行耗时（毫秒）
       * @param status 调用结果状态
       * @param failureReason 失败或阻断原因（成功时为 null）
       */
      void record(String toolName, long durationMs, InvocationStatus status, String failureReason);

      /**
       * 获取当前度量快照。
       *
       * @return 内存度量快照数据
       */
      McpMetricsSnapshot getSnapshot();
  }
  ```

#### 1.2 高性能无锁内存统计实现 (`InMemoryMcpMetricsCollector`)
- 采用 `java.util.concurrent.atomic.LongAdder` 与 `AtomicLong`，保证高并发调用下的高性能、无锁计数与最低系统开销。
- 维护全局维度与单个工具 (`toolName`) 维度的统计：
  - `totalInvocations`：总调用次数；
  - `successInvocations`：成功调用次数；
  - `deniedInvocations`：ACL 阻断次数；
  - `validationFailures`：参数校验失败次数；
  - `errorInvocations`：异常失败次数；
  - `totalDurationMs`、`maxDurationMs`：耗时统计与平均耗时衍生计算。
- 提供不可变快照模型 `McpMetricsSnapshot` 与 `ToolMetricsSnapshot`，支持时间截面只读导出。

#### 1.3 治理指标收集拦截器 (`MetricsMcpInterceptor`)
- 实现 `McpInterceptor` 契约，排序号定义为 **`getOrder() = -100`**（最高执行优先级之一）：
  - **前置阶段 (`preHandle`)**：在调用上下文属性 `attributes()` 中压入开始时间戳 `_mcp_start_time`；
  - **后置阶段 (`postHandle`)**：若责任链正常返回，计算耗时并回调 `collector.record(toolName, duration, InvocationStatus.SUCCESS, null)`；
  - **异常阶段 (`onError`)**：若后续拦截器（如 ACL、参数校验）抛出异常或底层执行崩溃：
    - `McpAccessDeniedException` $\rightarrow$ 标记 `ACCESS_DENIED`；
    - `McpGovernanceException` $\rightarrow$ 标记 `VALIDATION_FAILED`；
    - 其余异常 $\rightarrow$ 标记 `ERROR`；
    - 计算耗时并回调 `collector.record(toolName, duration, status, ex.getMessage())`。
- **责任链顺序确立**：
  $$\text{Metrics (-100)} \rightarrow \text{Tool ACL (100)} \rightarrow \text{Param Validation (200)} \rightarrow \text{Logging (1000)}$$
  由于 `MetricsMcpInterceptor` 处在最外层，其 `onError` 能够 100% 捕获后续 ACL 阻断与参数校验拒绝，彻底杜绝度量逃逸。

---

### 2. Spring Boot 观测集成与条件装配（置于 `mcp-governance-spring-boot-starter`）

#### 2.1 可选依赖声明 (Optional Dependencies)
在 `mcp-governance-spring-boot-starter/pom.xml` 中引入如下可选依赖，设置 `<optional>true</optional>`，由宿主项目自主决定是否启用：
- `io.micrometer:micrometer-core`
- `org.springframework.boot:spring-boot-actuator`
- `org.springframework.boot:spring-boot-actuator-autoconfigure`

#### 2.2 Micrometer 仪表盘桥接适配 (`MicrometerMcpMetricsCollector`)
- 当且仅当存在 `io.micrometer.core.instrument.MeterRegistry` 时装配：
- 自动向注册表绑定多维度指标（标准命名空间前缀 `mcp.governance.`）：
  1. `mcp.governance.invocations` (Counter)：工具调用总数，标签包括 `tool={toolName}`, `status={SUCCESS|ACCESS_DENIED|VALIDATION_FAILED|ERROR}`；
  2. `mcp.governance.execution.time` (Timer)：成功执行的工具耗时分布直方图，标签 `tool={toolName}`；
  3. `mcp.governance.denied.total` (Counter)：策略阻断与校验违规专用计数器，标签 `tool={toolName}`, `type={ACL|VALIDATION}`。
- 内部同时维护 `InMemoryMcpMetricsCollector` 代理，兼顾外部 Prometheus 拉取与内部 Actuator 端点查询。

#### 2.3 自定义 Spring Boot Actuator 端点 (`McpGovernanceEndpoint`)
- 提供注解驱动端点 `@Endpoint(id = "mcpGovernance")`（访问端点路径：`/actuator/mcpGovernance` 或兼容连字符 `/actuator/mcp-governance`）；
- 使用条件注解 `@ConditionalOnClass(Endpoint.class)` 与 `@ConditionalOnAvailableEndpoint(endpoint = McpGovernanceEndpoint.class)`；
- 暴露 `@ReadOperation` 查询方法：
  返回统一的 JSON 治理状态快照结构，包含：
  - `status`: `"UP"`；
  - `uptimeMs`: 治理层运行毫秒数；
  - `summary`: 全局调用总量、成功率、阻断总量、平均耗时；
  - `tools`: Map 结构列出各 Tool 的实时度量（调用次数、P95/平均耗时估算、最近阻断原因）。

#### 2.4 配置扩展 (`McpGovernanceProperties`)
新增 `metrics` 节点：
```yaml
mcp:
  governance:
    enabled: true
    metrics:
      enabled: true                  # 是否开启运行时指标遥测，默认 true
      micrometer-enabled: true       # 在存在 MeterRegistry 时是否绑定 Micrometer，默认 true
      export-to-actuator: true       # 是否注册 Actuator 监控端点，默认 true
```

---

## 影响与后果 (Consequences)

### 正面影响
1. **全面覆盖 OWASP MCP08**：填补了审计与遥测空白，运维人员与安全团队能直观观测工具调用频率、成功率、耗时走势与越权拦截事件。
2. **严格保持分层洁癖**：`core` 模块零外部依赖，使用标准 Java 17 并发原子类支撑无锁高性能统计；Starter 依赖全部采用 `<optional>true</optional>` 与 `@ConditionalOnClass` 条件装配，杜绝依赖污染。
3. **责任链架构闭环**：`MetricsMcpInterceptor` (Order: -100) 位于最外围包裹层，实现对安全策略决策与底层执行的全生命周期准确度量。
4. **多生态无缝兼容**：既支持企业级 Prometheus / Grafana 指标抓取，又支持轻量 Spring Boot Actuator 端点实时查看。

### 权衡与取舍 (Trade-offs)
- 内存指标快照在超多 Tool（如上万动态 Tool）场景下的内存开销：
  - *应对措施*：工具维度指标采用基于 `ConcurrentHashMap` 的按需延迟初始化机制，只记录被实际调用过的工具，且快照生成采用不可变纯数据 DTO。

---

## 替代方案 (Alternatives Considered)

1. **直接在 `mcp-governance-core` 中引入 Micrometer API 依赖**：
   - *否决原因*：严重违反《AGENTS.md》第 4 节“core 保持纯 Java，无框架依赖”的最高红线。纯 Java 契约 + 扩展 SPI 能够保证 CLI 工具与非 Spring 场景下的超轻量运行。
2. **仅在日志拦截器 (`LoggingMcpInterceptor`) 中打印耗时，不做指标计数**：
   - *否决原因*：纯文本日志无法被告警监控系统（如 Prometheus、Alertmanager）直接量化聚合，无法实现自动化熔断与大盘展示。
3. **使用 AOP 动态代理切面实现遥测**：
   - *否决原因*：AOP 强依赖 Spring 容器且对非 Spring 托管的 MCP 调用无效；责任链模式契约统一、确定性强、无反射性能损耗。

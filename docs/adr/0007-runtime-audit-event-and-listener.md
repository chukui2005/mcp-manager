# 0007. 运行时审计事件模型与事件监听器体系架构设计

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-31
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0004-spring-boot-starter-and-runtime-interception.md, docs/adr/0005-runtime-policy-and-tool-acl.md, docs/adr/0006-runtime-metrics-and-actuator.md, .worklog/TASK-007.md

---

## 背景 (Context)

在 Phase 2 的持续演进中，项目已依次构建了：
1. 运行时请求拦截基础契约与脱敏日志拦截器（TASK-004）；
2. 动态访问控制 Tool ACL 与防越权参数安全校验拦截器（TASK-005，治理 OWASP MCP02/MCP05）；
3. 运行时指标遥测与 Actuator 观测端点（TASK-006，治理 OWASP MCP08 遥测维度）。

至此，MCP 治理层在“控制阻断”与“统计大盘”两个维度已初具规模。然而，面对金融、企业级合规与安全运营中心（SOC / SIEM）的深度治理诉求，仍存在显著的**审计溯源与事件流转盲区**，直指 **OWASP MCP01（Token 与凭证泄露防范）** 与 **OWASP MCP08（审计与遥测缺失）** 的核心条款：
1. **审计记录不可变结构化缺失**：当前虽然具备纯文本日志打印（`LoggingMcpInterceptor`），但非结构化的自由文本难以被 Logstash、Fluentd、Elasticsearch 或 SIEM 系统高效提取、检索与关联分析，缺少不可变、规范化的审计事件对象（Audit Event Record）。
2. **事件分发与宿主解耦诉求**：企业宿主系统通常需要对高危工具调用进行二次流转（如写安全归档数据库、触发钉钉/企业微信安全告警、提交审批流或推送到 Kafka）。治理层必须提供标准的**事件发布者/监听器（Publisher / Listener）机制**，且支持纯 Java SPI 与 Spring Framework 事件总线无缝集成。
3. **架构强隔离红线（不可协商）**：
   - 根据《AGENTS.md》第 4 节：`mcp-governance-core` 必须保持**纯 Java 17，严禁引入 Spring 依赖**。审计事件领域模型、发布器抽象与核心拦截逻辑必须在 `core` 模块中实现。
   - 根据《AGENTS.md》第 6 节：控制面模型与 Wire 传输模型强隔离，不得将任何特定 MCP SDK 的内部 DTO 耦合进审计事件模型中。
4. **敏感凭证防泄漏协同（OWASP MCP01）**：审计事件中记录的参数必须强制执行敏感字段打码与脱敏，严禁任何 Token、Password、Secret 或 Private Key 明文沉降至审计流中。
5. **责任链执行次序编排**：审计拦截器必须精确放置在拦截链的合适层级，确保既能捕获成功执行结果与耗时，又能 100% 捕获 Tool ACL 阻断、参数校验失败与未知异常，杜绝审计逃逸。

---

## 架构决策 (Decision)

### 1. 纯 Java 审计事件领域模型与 SPI 抽象（置于 `mcp-governance-core`）

在 `io.github.chukui2005.mcpgov.core.runtime.audit` 包下定义纯 Java 17 领域模型，保持零第三方依赖（仅基于 Java 标准库与基础 slf4j/jackson）：

#### 1.1 不可变结构化审计事件 (`McpAuditEvent`)
采用 Java 17 `record` 建立不可变审计事件领域对象：
- `eventId`: UUID 唯一标识字符串，用于事件去重与全局审计追踪；
- `timestamp`: 事件产生时间戳 (`Instant`)；
- `traceId`: 分布式链路追踪 ID（若上下文无显式提供则自动生成或沿用会话追踪标识）；
- `callerId`: 调用方/Agent 身份标识（来自上下文属性 `attributes`，允许为空）；
- `toolName`: 调用的 MCP Tool 名称；
- `arguments`: **已脱敏**的入参映射快照 `Map<String, Object>`（只读防篡改视图）；
- `status`: 调用状态 `InvocationStatus` (`SUCCESS`, `ACCESS_DENIED`, `VALIDATION_FAILED`, `ERROR`)；
- `durationMs`: 工具执行总耗时毫秒数；
- `failureReason`: 阻断或失败简报（正常调用为 `null`）；
- `attributes`: 会话元数据与环境快照（只读 Map，包含客户端 IP、ACL 模式、规则匹配快照等）。

#### 1.2 敏感数据脱敏工具 (`SensitiveDataMasker`)
将脱敏逻辑从单一的日志拦截器下沉并升华为纯 Java 的领域安全服务 `SensitiveDataMasker`：
- 内置敏感键名特征词表（`password`, `secret`, `token`, `apikey`, `api_key`, `authorization`, `privatekey`, `private_key` 等，不区分大小写）；
- 支持递归脱敏嵌套 Map 与多层结构，将敏感值替换为安全掩码 `******`；
- 在生成 `McpAuditEvent` 时强制执行脱敏，实现底层防逃逸保障（OWASP MCP01）。

#### 1.3 审计发布器与监听器 SPI 契约
- **`McpAuditListener`**：函数式监听器接口：
  ```java
  @FunctionalInterface
  public interface McpAuditListener {
      void onAuditEvent(McpAuditEvent event);
  }
  ```
- **`McpAuditPublisher`**：审计发布器接口：
  ```java
  public interface McpAuditPublisher {
      void publish(McpAuditEvent event);
      void addListener(McpAuditListener listener);
  }
  ```
- **`DefaultMcpAuditPublisher`**：纯 Java 实现，维护并发安全的监听器列表（`CopyOnWriteArrayList`）。在分发事件时进行异常隔离沙箱保护（单个监听器抛出异常仅记录错误日志，绝不中断其他监听器执行，更不得阻断 MCP 主调用流程）。

#### 1.4 运行时审计拦截器 (`AuditMcpInterceptor`)
实现 `McpInterceptor` 契约，设定排序号为 **`getOrder() = -50`**：
- **执行优先级设计**：
  $$\text{Metrics (-100)} \rightarrow \mathbf{Audit (-50)} \rightarrow \text{Tool ACL (100)} \rightarrow \text{Param Validation (200)} \rightarrow \text{Logging (1000)}$$
- **生命周期处理**：
  - **前置阶段 (`preHandle`)**：捕获开始时间，注入追踪 ID（若无），将上下文深拷贝并准备审计上下文；
  - **后置阶段 (`postHandle`)**：在工具成功执行完毕后触发，计算耗时，提取脱敏参数与成功标识，构建状态为 `SUCCESS` 的 `McpAuditEvent`，并调用 `publisher.publish(event)`；
  - **异常阶段 (`onError`)**：当 Tool ACL 阻断（抛出 `McpAccessDeniedException`）、参数校验失败（抛出 `McpGovernanceException`）或底层工具异常抛出时，`onError` 被触发。识别具体失败状态（`ACCESS_DENIED` / `VALIDATION_FAILED` / `ERROR`），捕获阻断详情，构建审计事件并发布。
- **审计零遗漏保障**：因为 `AuditMcpInterceptor` (Order: -50) 位于 ACL (100) 和参数校验 (200) 的外层，无论是正常执行还是任何安全阻断，均无法逃逸出审计事件捕获链路。

---

### 2. Spring Boot 桥接与结构化 JSON 导出（置于 `mcp-governance-spring-boot-starter`）

#### 2.1 Spring 事件总线桥接 (`SpringMcpAuditPublisher`)
- 实现 `McpAuditPublisher` 并感知 Spring 容器环境（注入 `ApplicationEventPublisher`）；
- 定义 `SpringMcpAuditEvent extends ApplicationEvent`：将核心 `McpAuditEvent` 载荷无缝包装为标准 Spring 事件；
- 宿主系统开发者可直接在业务 Bean 中使用原生注解消费审计事件：
  ```java
  @EventListener
  @Async // 支持异步解耦
  public void handleAudit(SpringMcpAuditEvent springEvent) {
      McpAuditEvent event = springEvent.getAuditEvent();
      // 推送 SIEM、存储归档或安全告警
  }
  ```

#### 2.2 结构化 JSON 审计日志监听器 (`JsonLoggingMcpAuditListener`)
- 实现 `McpAuditListener` 接口；
- 使用 Jackson 的 `ObjectMapper` 将 `McpAuditEvent` 序列化为规范紧凑的单行 JSON 字符串；
- 输出到专属的 SLF4J Logger（默认 `io.github.chukui2005.mcpgov.audit`），便于运维团队在 `logback.xml` 中配置独立的 Audit Appender（如滚动写入 `mcp-audit.log`）；
- 单行 JSON 结构满足 Elasticsearch、Splunk、Loki 零正则开箱即用的采集索引要求。

#### 2.3 属性扩展与条件装配
在 `McpGovernanceProperties.AuditProperties` 中扩展结构化导出与事件发布配置：
```yaml
mcp:
  governance:
    audit:
      enabled: true
      log-level: INFO
      mask-sensitive-arguments: true
      json-export:
        enabled: true
        logger-name: "io.github.chukui2005.mcpgov.audit"
        pretty-print: false
      spring-event:
        enabled: true
```
在 `McpGovernanceAutoConfiguration` 中：
- 自动发现容器中的所有 `McpAuditListener` Bean 并注册到发布器中；
- 自动注册 `SpringMcpAuditPublisher`（若未自定义）；
- 自动注册 `JsonLoggingMcpAuditListener`（`@ConditionalOnProperty(name = "mcp.governance.audit.json-export.enabled", havingValue = "true", matchIfMissing = true)`）；
- 注册 `AuditMcpInterceptor` Bean（Order: -50）。

---

### 3. 契约 Schema (`docs/contracts/audit-event.schema.json`)
建立符合 JSON Schema Draft 2020-12 的标准化契约，作为审计事件在网络流转、日志归档与外部 SIEM 集成时的权威数据规约。

---

## 影响与后果 (Consequences)

### 正向影响 (Positive)
1. **全面合规闭环**：彻底满足 OWASP MCP01（凭据防泄露）与 OWASP MCP08（完整审计追踪）的合规硬性要求；
2. **架构强内聚零污染**：`mcp-governance-core` 保持纯 Java 17，无 Spring 依赖，核心 SPI 与事件模型具备跨框架通用性；
3. **极佳的扩展性**：支持纯 Java 监听器、Spring 原生 `@EventListener` 以及开箱即用的 JSON 日志采集三种集成路径；
4. **故障隔离与鲁棒性**：发布器内置异常沙箱隔离，审计监听器的任何故障绝不波及 MCP 核心调用主干。

### 潜在挑战与妥协 (Trade-offs)
- **JSON 序列化微小性能开销**：在高吞吐场景下，将每次调用序列化为 JSON 产生轻量对象分配。通过单行紧凑模式、无缩进以及高效 Jackson 流式处理，该开销控制在微秒级，完全处于可接受范围。

---

## 备选方案权衡 (Alternatives Considered)

### 备选方案 A：直接复用 Spring `ApplicationEvent` 作为核心事件模型
- **放弃原因**：严重违反《AGENTS.md》第 4 节关于 `mcp-governance-core` 绝对禁止依赖 Spring 的硬性红线。纯 Java 核心模型是 CLI 轻量嵌入和未来跨框架运行的前提。

### 备选方案 B：仅保留文本 Logging 拦截器，不引入结构化事件对象
- **放弃原因**：非结构化日志在面临多字段查询、统计大盘、SIEM 实时过滤阻断事件时存在巨大劣势，无法满足企业级安全运维需求。

### 备选方案 C：在 `MetricsMcpInterceptor` 内部兼职发布审计事件
- **放弃原因**：违反单一职责原则（SRP）。度量（Metrics）关注数值聚合与性能分布，审计（Audit）关注单次调用的完整上下文溯源与安全违规记录。将两者拆分为 Order: -100 与 Order: -50 的独立拦截器，职责更为纯粹解耦。

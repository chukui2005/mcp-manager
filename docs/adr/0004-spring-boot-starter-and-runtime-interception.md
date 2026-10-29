# 0004. Spring Boot Starter 模块脚手架与运行时请求拦截契约设计

* **状态**: 已接受 (Accepted)
* **日期**: 2026-10-29
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0001-project-scope-and-modules.md, docs/adr/0002-mcp-config-parser-and-owasp-rules.md, docs/handover.md

---

## 背景 (Context)

在 Phase 1 中，本项目已顺利实现了针对 MCP 配置的静态扫描引擎、OWASP MCP Top 10（MCP01/MCP02/MCP03）规则集、Picocli CLI、GitHub Action CI 自动化审计以及双语 README 交付，形成了离线安全扫描基线能力。

进入 Phase 2（运行时治理），治理边界由“静态配置审计”向“实时调用拦截与策略执行”拓展：
1. **轻量非侵入集成诉求**：Java 生态广泛采用 Spring Boot，用户迫切需要一个即插即用（Plug-and-Play）的 `mcp-governance-spring-boot-starter`，仅需引入 Maven 依赖并在 `application.yml` 中配置即可激活运行时治理。
2. **纯 Java 核心与模块解耦约束**：依据《AGENTS.md》第 4 节与第 5.1 节硬性约束，`mcp-governance-core` 必须保持纯 Java 17，**严禁依赖 Spring 框架**。运行时拦截契约的核心抽象必须属于独立于框架的领域模型，以便后续支持 Quarkus、Micronaut 或纯 Java 网关。
3. **控制面模型与 Wire 传输模型强隔离**：依据《AGENTS.md》第 6.1 节，MCP Java SDK 或底层 JSON-RPC 传输层的内部数据结构（如 `McpSchema.CallToolRequest`、`CallToolResult`）频繁变动且偏向底层协议。治理拦截器绝不能直接引用 SDK 内部类型，必须建立纯粹的控制面调用上下文抽象。
4. **安全默认与条件装配机制**：Starter 必须遵循条件装配标准（`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`），在未显式激活或缺乏运行时类时能够平滑降级，不阻断宿主业务。

---

## 架构决策 (Decision)

### 1. 模块边界与依赖分层

设立独立的 Starter 模块，形成以下依赖关系：
```
[宿主应用 / Spring Boot Application]
       │
       ▼
[mcp-governance-spring-boot-starter] (Spring Boot 3.3+ 自动装配、配置绑定)
       │
       ▼
[mcp-governance-core] (纯 Java 17，无 Spring 依赖；定义核心拦截接口与不可变上下文)
```

- **`mcp-governance-core`**：引入运行时接口抽象包 `io.github.chukui2005.mcpgov.core.runtime.*`，仅包含不可变领域对象（Record）、拦截器接口（Interface）与核心责任链执行器（纯 Java 实现），依赖维持在基础 `jackson` 与 `slf4j`。
- **`mcp-governance-spring-boot-starter`**：
  - 依赖 `mcp-governance-core`；
  - 依赖 `spring-boot-autoconfigure`（`optional: true` / `provided`）；
  - 依赖 `spring-boot-configuration-processor`（`optional: true` 生成元数据）；
  - 采用 Spring Boot 3.x 标准自动配置声明文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

### 2. 运行时请求拦截契约设计 (纯 Java 领域模型)

在 `mcp-governance-core` 中定义清晰的调用生命周期责任链模型：

1. **调用上下文 `McpInvocationContext` (不可变 Record)**：
   - `toolName`: 调用的工具名称（非空）；
   - `arguments`: 工具入参 `Map<String, Object>`（不可变防篡改视图）；
   - `attributes`: 会话元数据 `Map<String, Object>`（如 `traceId`, `userId`, `clientIp`, `callerId` 等附加上下文）；
   - `timestamp`: 请求进入拦截链的初始时间戳 (`Instant`)；
   - 提供不可变变更辅助器 `withAttribute(String key, Object value)` 与 `withArguments(Map<String, Object> newArgs)`，便于合规脱敏或上下文注入。

2. **调用结果 `McpInvocationResult` (不可变 Record)**：
   - `success`: 执行是否成功 (`boolean`)；
   - `result`: 工具执行返回值（任意纯 Java 对象或 `Map`）；
   - `errorMessage`: 异常或失败简报（若存在）；
   - `durationMs`: 执行耗时毫秒数；
   - 提供工厂方法 `success(Object result, long durationMs)` 与 `failure(String errorMessage, long durationMs)`。

3. **拦截器契约 `McpInterceptor` (接口)**：
   - 具备排序能力：继承或支持 `int getOrder()`，默认 `0`；
   - 前置拦截 `default McpInvocationContext preHandle(McpInvocationContext context)`：支持参数校验、权限判定、敏感入参脱敏。若鉴权失败可抛出 `McpAccessDeniedException` 快速阻断；
   - 后置拦截 `default McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result)`：支持出参审计、结果敏感信息脱敏与遥测日志记录；
   - 异常拦截 `default void onError(McpInvocationContext context, Throwable throwable)`：捕获执行异常，记录审计追踪并支持告警。

4. **拦截执行链 `McpInterceptorChain` (责任链执行器)**：
   - 纯 Java 实现，维护有序的 `List<McpInterceptor>`；
   - 提供 `executePreHandle(...)`、`executePostHandle(...)` 与 `executeOnError(...)` 标准执行逻辑；
   - 当任一拦截器抛出安全异常时，立即停止向下传递（Fail-Fast）。

### 3. 控制面模型与 Wire 传输模型强隔离

- 任何外部 MCP SDK（例如 `io.modelcontextprotocol.sdk` 或 Spring AI MCP 模块）的内部类、数据传输对象（DTO）均**不得**出现在 `McpInvocationContext` 和 `McpInterceptor` 的公开方法签名中；
- 适配层（Adapter）负责将外部 Wire 载荷转换映射为 `McpInvocationContext`，经治理链执行处理后，再将合规的出参或阻断异常转换回宿主框架响应。

### 4. Spring Boot Starter 自动装配与条件激活

在 `mcp-governance-spring-boot-starter` 中实现自动装配：
- **`McpGovernanceAutoConfiguration`**：
  - 标注 `@AutoConfiguration`；
  - 标注 `@ConditionalOnClass(McpInterceptor.class)`；
  - 标注 `@EnableConfigurationProperties(McpGovernanceProperties.class)`；
  - 标注 `@ConditionalOnProperty(prefix = "mcp.governance", name = "enabled", havingValue = "true", matchIfMissing = true)`；
  - 注册 `McpInterceptorChain` Bean，并标注 `@ConditionalOnMissingBean`；
  - 自动发现容器中所有 `McpInterceptor` Bean，按 `Ordered` 或 `@Order` 排序注入链中；
  - 注册开箱即用的默认审计日志拦截器 `LoggingMcpInterceptor`（当 `mcp.governance.audit.enabled=true` 时）。

### 5. 治理配置属性模型 (`mcp.governance`)

创建 `McpGovernanceProperties`，严格满足以下规范：
- 前缀：`mcp.governance`；
- 全字段具备标准 Javadoc、默认值及 getter/setter：
  - `enabled`: `boolean`，默认 `true`（总开关）；
  - `failFast`: `boolean`，默认 `true`（前置鉴权阻断时立即终止调用）；
  - `audit`: 内部静态类：
    - `enabled`: `boolean`，默认 `true`（启用调用审计日志）；
    - `logLevel`: `String`，默认 `"INFO"`；
    - `maskSensitiveArguments`: `boolean`，默认 `true`（自动对包含 `password`, `token`, `secret`, `key` 等敏感字段打码掩码）。

---

## 后果与影响 (Consequences)

### 正面影响
1. **架构边界清晰**：`core` 专注于核心治理契约，实现完全与框架解耦；Starter 专注于 Spring 生态自动装配，职责分明。
2. **渐进式平滑接入**：宿主 Spring Boot 项目只需增加 Starter 依赖，即可通过 Bean 注入方式自定义任意 `McpInterceptor`，无缝嵌入业务鉴权与审计流。
3. **安全开箱即用**：默认激活调用审计与敏感字段脱敏，符合 OWASP MCP01/MCP08 治理基线。
4. **具备向后兼容性**：不限制底层是 STDIO 还是 SSE/Streamable HTTP，只需适配器传入通用 `McpInvocationContext`。

### 潜在制约与应对 (Trade-offs)
- **拦截器性能开销**：在高并发大模型 Tool Call 场景下，拦截链的链式调用可能增加纳秒级延迟。
  - *应对措施*：拦截上下文采用高效不可变 Record 实现，链条遍历无锁，敏感字段正则匹配仅对顶层 Map 进行高效过滤。

---

## 替代方案 (Alternatives Considered)

1. **将 Spring 依赖直接引入 `core`**：
   - *否决原因*：严重违背《AGENTS.md》第 4 节“`core` 不得依赖 Spring”的绝对红线，会导致 CLI 膨胀且破坏后续支持其它运行时的灵活性。
2. **基于 Spring AOP `@Around` 实现拦截**：
   - *否决原因*：Spring AOP 强依赖于 Spring 容器管理的目标 Bean 代理，无法治理非 Spring 管理的 SDK 原生 Client/Server 连接会话或动态派发的 Tool 调用，且排查栈深复杂。基于明确契约的显式 `McpInterceptor` 责任链机制更为健壮可控。

# 0009. 网关治理流水线贯通、长连接自愈与协议规范对齐加固

* **状态**: 提议中 (Proposed)
* **日期**: 2026-11-02
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0008-phase3-gateway-architecture-preview.md, .worklog/TASK-013.md

---

## 背景 (Context)

在 TASK-009 ~ TASK-012 陆续交付了 `mcp-governance-gateway` 的基础骨架、动态路由加载、多协议出站客户端（HTTP/SSE/STDIO）以及子进程池托管后，全面审查发现了若不立即加固将导致重大生产隐患与协议偏差的关键缺陷：

1. **【CRITICAL】治理拦截链未实际接入执行 (Security Bypass - OWASP MCP02/MCP08)**：
   Core 模块已具备完善的 `McpInterceptorChain`（包含了 `ToolAclMcpInterceptor`、`ParameterValidationMcpInterceptor`、`MetricsMcpInterceptor`、`AuditMcpInterceptor`）。然而在 `DefaultGatewayExecutionAdapter` 与 `McpRpcController` 中，工具调用直接穿透到了下游客户端，核心治理拦截链处于“已编译未挂载”的旁路状态。这导致未经授权的工具调用、越权参数、违规访问无法被拦截，且缺乏统一审计日志产生。
2. **【MAJOR】错误伪造为成功 Mock 回退 (False Success Anti-Pattern)**：
   在 `DefaultGatewayExecutionAdapter` 中，当远端服务连接失败（如 Connection Refused）或抛出异常时，代码通过 `fallbackMockResult` 捕获并将状态篡改为 `status="SUCCESS"` 返回给调用方。这严重违背了系统可用性与错误透明原则，掩盖了真实下游故障，导致 Agent 误以为命令已执行成功。
3. **【MAJOR】SseUpstreamClient 响应队列内存泄漏 (Memory Leak via Hanging Sinks)**：
   在 `SseUpstreamClient.sendRpcAndAwait` 中，每发起一个请求便在 `pendingRequests` Map 中放入一个 `Sinks.One`。当请求超时（`requestTimeout`）或网络中断被客户端取消时，未能通过 `doFinally` 从 Map 中移除该 Sink，导致长时间高并发运行或下游频繁超时下，`pendingRequests` 持续膨胀直至内存耗尽 (OOM)。
4. **【MAJOR】MCP 协议生命周期与握手偏差 (Protocol Lifecycle Misalignment)**：
   - 现有的 `McpRpcController` 仅处理了 `initialize`、`tools/list`、`tools/call`，缺少对 MCP 规范核心保活方法 `ping` 的处理；
   - 按照 MCP 规范，客户端在 `initialize` 完成后必须发送通知 `notifications/initialized`。对于 JSON-RPC 通知（`id == null`），网关必须静默处理，返回空响应（HTTP 204 No Content 或空响应体），而当前网关将其作为错误方法返回 `-32601`；
   - 出站客户端（HTTP/SSE）在建立连接后未向下游主动发起握手，可能导致部分严格遵循规范的 MCP Server 拒绝服务。
5. **【MAJOR】长连接脆弱性与瞬时网络重试 (Lack of Connection Resilience)**：
   `SseUpstreamClient` 一旦发生网络断开，原有的连接直接终结，缺乏指数退避自动重连机制；对于幂等方法（如 `tools/list`、`ping`）在遭遇瞬时网络抖动时缺乏轻量级自愈重试。
6. **【MAJOR】跨域安全防护未实际装配 (CORS Unenforced)**：
   网关虽有 `corsAllowedOrigins` 配置项，但未在 Spring WebFlux 中装配 `CorsWebFilter`，导致浏览器端发起的 MCP 请求缺乏统一的安全跨域准入。
7. **【MINOR】上下文属性命名不一致与正则编译开销**：
   `DefaultGatewayExecutionAdapter` 中使用了 `callerApplication`，而 Core 的 `AuditMcpInterceptor` 期待 `callerId`；`TokenRefResolver` 中的部分正则未提炼为常量编译。

---

## 架构决策 (Decision)

### 1. 彻底打通网关治理责任链 (Connecting Governance Pipeline)

#### 1.1 治理责任链编排与注入
- 在 `DefaultGatewayExecutionAdapter` 中注入 Core 模块的 `McpInterceptorChain`（若容器中未显式配置，则通过默认策略构造标准拦截链：Metrics -> Audit -> ACL -> Validation）；
- 在工具调用执行 `executeReactive` 时，通过将下游客户端真实调用包装为 `McpInterceptorChain` 的终点端点（Terminal Target），确保入站工具调用必须依次流经：
  1. **`MetricsMcpInterceptor` (Order -100)**：记录指标（成功率、耗时、调用频度）；
  2. **`AuditMcpInterceptor` (Order -50)**：敏感字段脱敏并发布 `McpAuditEvent`；
  3. **`ToolAclMcpInterceptor` (Order 100)**：校验针对该租户/Caller 是否允许调用指定工具；
  4. **`ParameterValidationMcpInterceptor` (Order 200)**：按规则校验参数合法性（防注入/路径穿越）；
- 若链中任何拦截器抛出 `McpAccessDeniedException` 或 `McpGovernanceException`，立刻阻断向后分发，并由 `McpRpcController` 转换为标准 JSON-RPC Error 返回。

#### 1.2 统一上下文属性契约
统一上下文属性键命名：
```java
attributes.put(AuditMcpInterceptor.ATTR_CALLER_ID, safeClient);
attributes.put("serverId", serverId);
attributes.put("matchedPrefix", matchResult.getMatchedPrefix());
```

---

### 2. 清除伪造成功 Mock，确立真实错误暴露机制

- **移除伪造成功**：彻底废除 `fallbackMockResult` 将异常伪造为 `status=SUCCESS` 的逻辑；
- **透明暴露**：当路由无法匹配或未找到可执行端点时，返回明确的路由未找到错误；当下游连接超时或被拒绝时，返回包含明确诊断信息的失败结果 `McpInvocationResult.failure(...)` 或抛出治理异常，禁止欺骗上游模型。

---

### 3. SseUpstreamClient 内存泄露防御 (Zero Leak Sink Management)

在 `sendRpcAndAwait` 返回的 Mono 流上挂载 `doFinally` 信号监听：
```java
return sink.asMono()
    .doFinally(signalType -> {
        // 无论正常完成、超时报错还是下游 cancel，均确保从 Map 中移除
        pendingRequests.remove(reqId);
    });
```
确保无论在超时触发（`timeout(requestTimeout)`）、网络断开还是下游消费者提前取消时，对应的 `reqId` 都能 100% 从 `pendingRequests` 中逐出，彻底根除内存泄露隐患。

---

### 4. MCP 生命周期与协议规范对齐

#### 4.1 服务端生命周期对齐 (`McpRpcController`)
1. **支持 `ping` 方法**：直接响应空对象或 `{"status": "ok"}`（JSON-RPC Success）；
2. **支持 `notifications/initialized` 规范通知**：
   - 识别通知特征：`request.getId() == null` 或方法名为 `notifications/initialized`；
   - 对于通知请求，网关记录审计/调试日志，不返回包含错误或结果的 JSON-RPC 响应体（返回 HTTP 200/204 且 body 为空，或由 Controller 返回 `Mono.empty()`）；
3. **生命周期方法规范识别**：统一放行 `initialize` 与 `notifications/initialized`，不受工具 ACL 干扰。

#### 4.2 出站握手能力增强 (`UpstreamClient`)
在出站客户端（`HttpUpstreamClient` / `SseUpstreamClient`）初始化建立连接时，支持可选发送出站握手 `initialize` 请求及随后的 `notifications/initialized` 通知，实现双向标准对齐。

---

### 5. 长连接自愈与轻量重试

1. **SSE 断线指数退避重连**：
   在 `SseUpstreamClient.connectSse()` 的 `error` 与 `complete` 回调中，若当前客户端未显式 `close()`，引入基于 Reactor 的指数退避重连（初始 1s，最大 30s，随机抖动），自动恢复 SSE 监听流；
2. **幂等方法轻量重试**：
   针对 `tools/list` 与 `ping` 探测，若遭遇网络瞬时 I/O 异常，采用 `retryWhen(Retry.backoff(2, Duration.ofMillis(500)))` 进行轻量自愈，提升弱网环境下的系统韧性。

---

### 6. 网关跨域安全过滤器 (CORS Hardening)

在 `mcp-governance-gateway` 的配置类中，基于 `GatewayServerProperties.getCorsAllowedOrigins()` 配置注册响应式 `CorsWebFilter`：
- 若未配置或配置为空，默认仅允许本地同源请求；
- 若配置了允许的 Origin 列表，则严格配置 `CorsConfiguration`：
  - `allowedOrigins` 匹配白名单；
  - `allowedMethods` 允许 POST, GET, OPTIONS；
  - `allowedHeaders` 允许 Authorization, Content-Type, Accept, X-Requested-With；
  - `allowCredentials` 设为 false（防止凭证泄露）。

---

### 7. 正则预编译与性能微调

在 `TokenRefResolver` 中，将直接调用 `trimmed.matches("^[a-zA-Z0-9_]+$")` 优化为静态常量预编译 `Pattern`：
```java
private static final Pattern PLAIN_ENV_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
```
避免每次解析环境变量时的重复正则编译开销。

---

## 结果与收益 (Consequences)

### 正向收益 (Positive)
1. **闭环安全治理**：网关终于具备实质性的 Tool ACL 租户隔离、入参合规校验与审计记录，彻底消除了旁路漏洞；
2. **高保真故障反馈**：下游错误如实向上游报告，消除了伪造成功导致的静默故障与不可预测副作用；
3. **高韧性生产可用**：Sse 内存泄漏修复与断网自愈重连使网关具备长期无人值守运行的稳健性；
4. **完全协议兼容**：对齐 MCP 2024-11-05 规范生命周期（ping、notifications、握手）。

### 成本与权衡 (Trade-offs)
- 引入治理责任链后，每次工具调用增加微秒级的校验与脱敏开销（基于 Core 纯内存设计，性能损耗可忽略不计）。

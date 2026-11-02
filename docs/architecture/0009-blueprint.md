# 0009. 独立网关治理流水线贯通、长连接自愈与协议对齐技术蓝图

> **蓝图代号**: BLUEPRINT-0009  
> **关联任务**: TASK-013  
> **设计角色**: 架构师 (Architect)  
> **编写语言**: 中文 (规范遵循 `AGENTS.md`)  
> **前置依赖**: `docs/adr/0009-gateway-governance-pipeline-and-resilience.md`

---

## 1. 架构目标与加固全景

本次技术蓝图旨在指导研发工程师高效、精准、零破坏地完成对 `mcp-governance-gateway` 独立网关核心缺陷的加固与韧性增强：
1. **彻底贯通治理流水线**：将 `McpInterceptorChain` 与入站工具调用深度绑定，实现鉴权、校验、指标与审计闭环；
2. **清除伪造成功的 Mock 逻辑**：如实反映下游错误与连接异常；
3. **修复 SSE Sink 内存泄漏**：在流生命周期终结时通过 `doFinally` 安全注销；
4. **生命周期对齐**：完整支持 `ping`、`notifications/initialized` 与出站客户端握手；
5. **长连接自愈与轻量重试**：实现 SSE 指数退避断网重连与幂等方法重试；
6. **落地跨域安全**：装配 `CorsWebFilter`。

---

## 2. 核心组件交互与时序图

### 2.1 网关工具调用治理拦截贯通时序 (tools/call Execution Flow)

```
Client (Agent)            McpRpcController       DefaultGatewayExecutionAdapter       McpInterceptorChain       UpstreamClient (Downstream)
      |                          |                             |                              |                               |
      | 1. POST /mcp/v1/rpc      |                             |                              |                               |
      |    (tools/call, name)    |                             |                              |                               |
      |------------------------->|                             |                              |                               |
      |                          | 2. matchByToolName()        |                              |                               |
      |                          | 3. buildContext()           |                              |                               |
      |                          | 4. executeReactive(ctx)     |                              |                               |
      |                          |---------------------------->|                              |                               |
      |                          |                             | 5. chain.execute(ctx,        |                               |
      |                          |                             |       targetCallback)        |                               |
      |                          |                             |----------------------------->|                               |
      |                          |                             |                              | 6. Metrics.before()           |
      |                          |                             |                              | 7. Audit.before()             |
      |                          |                             |                              | 8. ToolAcl.check()            |
      |                          |                             |                              |    (若未授权抛 AccessDenied)   |
      |                          |                             |                              | 9. ParameterValidation.check()|
      |                          |                             |                              |    (若非法抛 GovernanceEx)    |
      |                          |                             |                              |                               |
      |                          |                             |                              | 10. targetCallback (真实调用) |
      |                          |                             |                              |------------------------------>|
      |                          |                             |                              |                               | 11. 远程 JSON-RPC tools/call
      |                          |                             |                              |                               |     (HTTP / SSE / STDIO)
      |                          |                             |                              |<------------------------------|
      |                          |                             |                              |                               | 12. 返回 McpInvocationResult
      |                          |                             |                              | 13. Audit.after() (脱敏审计)   |
      |                          |                             |                              | 14. Metrics.after() (记耗时)  |
      |                          |                             |<-----------------------------|                               |
      |                          |<----------------------------| 15. 返回治理后 Result         |                               |
      | 16. JSON-RPC 2.0 响应     |                             |                              |                               |
      |<-------------------------|                             |                              |                               |
```

---

## 3. 详细设计与代码契约

### 3.1 治理拦截链装配 (`DefaultGatewayExecutionAdapter`)

#### 依赖注入与初始化
```java
@Autowired(required = false)
private McpInterceptorChain interceptorChain;

@Autowired(required = false)
private McpMetricsCollector metricsCollector;

@Autowired(required = false)
private McpAuditPublisher auditPublisher;
```
若容器未注入现成的 `interceptorChain`，在 `@PostConstruct` 或构造函数中自动组装默认拦截链：
- `MetricsMcpInterceptor` (metricsCollector != null ? metricsCollector : new InMemoryMcpMetricsCollector())
- `AuditMcpInterceptor` (auditPublisher != null ? auditPublisher : new DefaultMcpAuditPublisher(), new SensitiveDataMasker())
- `ToolAclMcpInterceptor` (默认针对各个 route 从 `route.getGovernance()` 解析构造)
- `ParameterValidationMcpInterceptor`

#### 反应式与同步执行实现
在 `executeReactive(McpInvocationContext context)` 中：
```java
// 包装底层客户端真实调用为链终点 terminal function:
return Mono.defer(() -> {
    try {
        // 调用 interceptorChain 执行
        McpInvocationResult result = this.interceptorChain.execute(context, ctx -> {
            // 这里执行真实的底层调用逻辑（从 clientPool 获取 client 并执行 callTool）
            UpstreamClient client = clientPool.getClient(route);
            // 同步阻断或响应式转换
            return client.callTool(ctx.toolName(), ctx.arguments()).block();
        });
        return Mono.just(result);
    } catch (Throwable t) {
        return Mono.error(t);
    }
});
```
*注：对于 `McpAccessDeniedException` 和 `McpGovernanceException`，必须向外抛出或作为 `Mono.error(t)` 传递给 `McpRpcController` 进行统一错误码映射，禁止吞噬为成功。*

#### 统一上下文属性
```java
Map<String, Object> attributes = new HashMap<>();
attributes.put("serverId", serverId);
attributes.put(AuditMcpInterceptor.ATTR_CALLER_ID, safeClient);
attributes.put("matchedPrefix", matchResult.getMatchedPrefix());
```

---

### 3.2 清除伪造成功 Mock 回退

彻底移除 `fallbackMockResult` 中设置 `mockPayload.put("status", "SUCCESS")` 的逻辑。
- 当未匹配到路由或路由不可执行时：返回包含明确失败信息的 `McpInvocationResult.failure("下游服务不可达或未配置端点: " + serverId, durationMs)` 或抛出 `McpGovernanceException`；
- 当远程调用出现 `Connection refused` 或超时时：返回 `McpInvocationResult.failure("下游服务调用失败: " + e.getMessage(), durationMs)`，确保 `result.success() == false`。

---

### 3.3 SseUpstreamClient 响应队列防泄漏

在 `SseUpstreamClient.sendRpcAndAwait` 中：
```java
protected Mono<JsonRpcResponse> sendRpcAndAwait(JsonRpcRequest request) {
    String reqId = String.valueOf(request.getId());
    Sinks.One<JsonRpcResponse> sink = Sinks.one();
    pendingRequests.put(reqId, sink);

    String targetUri = this.messagePostUri != null && !this.messagePostUri.isEmpty()
            ? this.messagePostUri
            : this.sseUri;

    postWebClient.post()
            .uri(targetUri)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonRpcResponse.class)
            .subscribe(
                    directResponse -> {
                        if (directResponse != null) {
                            Sinks.One<JsonRpcResponse> pending = pendingRequests.remove(reqId);
                            if (pending != null) {
                                pending.tryEmitValue(directResponse);
                            }
                        }
                    },
                    error -> {
                        Sinks.One<JsonRpcResponse> pending = pendingRequests.remove(reqId);
                        if (pending != null) {
                            pending.tryEmitError(error);
                        }
                    }
            );

    // 核心加固：增加 doFinally 钩子，确保超时、异常中断或取消时安全移除
    return sink.asMono()
            .doFinally(signalType -> {
                pendingRequests.remove(reqId);
            });
}
```

---

### 3.4 MCP 生命周期协议对齐 (`McpRpcController`)

#### 1. 支持 `ping` 请求
```java
case "ping":
    Map<String, Object> pingResult = new HashMap<>();
    pingResult.put("status", "ok");
    return Mono.just(JsonRpcResponse.success(id, pingResult));
```

#### 2. 支持 `notifications/initialized` 通知
在 `handleRpc` 中增加通知检测：
```java
// 若为通知 (id == null 或者是通知方法)
if (request.getId() == null || "notifications/initialized".equals(method)) {
    log.debug("收到客户端通知: method={}, id={}", method, request.getId());
    // 按照 JSON-RPC 规范，通知不应有响应，在 WebFlux 中返回 Mono.empty()
    // 或返回专有空实体，Controller 方法签名可返回 Mono<ResponseEntity<JsonRpcResponse>>
    // 当为通知时返回 ResponseEntity.noContent().build() 或直接返回 null body
}
```
*提示：可将 `handleRpc` 返回值定义为 `Mono<ResponseEntity<JsonRpcResponse>>`，当收到通知且 `id == null` 时返回 `ResponseEntity.noContent().build()` 或 `ResponseEntity.ok().build()` 无包体。*

---

### 3.5 长连接自愈与重试逻辑

#### 1. SSE 断网指数退避重连 (`SseUpstreamClient`)
在 `connectSse()` 的错误与完成处理中：
```java
.subscribe(
    this::handleSseEvent,
    error -> {
        if (!closed) {
            log.warn("下游 SSE 路由 [{}] 连接中断: {}，准备重连...", routeId, error.getMessage());
            scheduleReconnect();
        }
    },
    () -> {
        if (!closed) {
            log.info("下游 SSE 路由 [{}] 连接断开，准备重连...", routeId);
            scheduleReconnect();
        }
    }
);
```
使用 Reactor 调度器延迟执行 `connectSse()`：
```java
private void scheduleReconnect() {
    if (closed) return;
    // 指数退避调度重连
    Mono.delay(Duration.ofSeconds(2))
        .subscribe(ignore -> connectSse());
}
```

#### 2. 幂等只读方法轻量重试
在 `listTools()` 和 `ping()` 中，针对偶发网络抖动增加 `retryWhen`：
```java
return sendRpcAndAwait(rpcRequest)
        .timeout(requestTimeout)
        .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofMillis(300))
                .filter(ex -> !(ex instanceof McpGovernanceException)))
        .map(...)
```

---

### 3.6 网关跨域安全过滤器装配 (`GatewayServerConfig`)

在 `mcp-governance-gateway` 的配置类 `GatewayServerConfig.java` 中注册 `CorsWebFilter`：
```java
@Bean
public CorsWebFilter corsWebFilter(GatewayServerProperties properties) {
    List<String> allowedOrigins = properties.getCorsAllowedOrigins();
    CorsConfiguration config = new CorsConfiguration();
    
    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
        for (String origin : allowedOrigins) {
            config.addAllowedOrigin(origin.trim());
        }
    } else {
        // 默认受限仅允许本地同源
        config.addAllowedOrigin("http://127.0.0.1");
        config.addAllowedOrigin("http://localhost");
    }
    
    config.addAllowedMethod(HttpMethod.POST);
    config.addAllowedMethod(HttpMethod.GET);
    config.addAllowedMethod(HttpMethod.OPTIONS);
    config.addAllowedHeader("*");
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
}
```

---

### 3.7 正则预编译常量优化 (`TokenRefResolver`)

```java
private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{([a-zA-Z0-9_]+)\\}$");
private static final Pattern PLAIN_ENV_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

// extractEnvVariableName 方法中使用预编译常量：
if (PLAIN_ENV_NAME_PATTERN.matcher(trimmed).matches()) {
    return trimmed;
}
```

---

## 4. 工程师实施边界与修改计划

### 4.1 主要修改文件 (Primary Files)
1. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/adapter/DefaultGatewayExecutionAdapter.java`
   - 装配并执行 `McpInterceptorChain`；
   - 清除伪造成功 Mock 回退，替换为透明错误；
   - 统一使用 `AuditMcpInterceptor.ATTR_CALLER_ID`。
2. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/controller/McpRpcController.java`
   - 支持 `ping` 请求；
   - 支持 `notifications/initialized` 等通知请求（无 id 返回空响应）；
   - 正确捕获并转换 `McpAccessDeniedException` 与 `McpGovernanceException`。
3. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/upstream/client/SseUpstreamClient.java`
   - `sendRpcAndAwait` 增加 `doFinally` 杜绝 Sink 内存泄漏；
   - 增加断线指数退避重连机制；
   - `listTools` 增加幂等安全重试。
4. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/config/GatewayServerConfig.java`
   - 装配响应式 `CorsWebFilter`。
5. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/upstream/auth/TokenRefResolver.java`
   - 预编译 `PLAIN_ENV_NAME_PATTERN`。

### 4.2 需要新增/修改的测试文件 (Test Files)
1. `mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/controller/GatewayGovernancePipelineIntegrationTest.java` (新增)
   - 测试发起 tools/call 时，若工具被 ToolAcl 拦截，直接返回 `-32003` 访问拒绝，不穿透到下游；
   - 测试入参被 ParameterValidation 拦截；
   - 测试审计事件正常产生；
   - 测试下游连接拒绝时如实返回错误，不再伪造为 SUCCESS。
2. `mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/upstream/client/SseUpstreamClientTest.java` (修改)
   - 验证超时后 `pendingRequests` 正常注销（无内存泄漏）；
   - 验证重连机制与 `ping`。
3. `mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/controller/McpRpcControllerTest.java` (修改)
   - 验证 `ping` 方法返回；
   - 验证 `notifications/initialized` 返回空/无内容。

### 4.3 实施与编辑顺序
1. **第一步**：优化 `TokenRefResolver` 与装配 `GatewayServerConfig.corsWebFilter`；
2. **第二步**：修复 `SseUpstreamClient` 的 `doFinally` 内存泄漏防护与断线重连；
3. **第三步**：在 `DefaultGatewayExecutionAdapter` 中装配 `McpInterceptorChain` 并清除假成功 Mock；
4. **第四步**：在 `McpRpcController` 中支持 `ping` 与通知；
5. **第五步**：补齐全量测试用例并执行 `mvn -B verify`。

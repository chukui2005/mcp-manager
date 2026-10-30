# 0007. TASK-007 技术实施蓝图：运行时审计记录结构化导出与事件监听器体系

## 1. 架构总览与全链路审计事件流转

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │ 外部调用者 / Agent / 大模型                                               │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (1) 调用工具: toolName, arguments, attributes
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ McpGovernanceAdapter (端到端统一治理适配器)                              │
 │ 构造初始不可变 McpInvocationContext                                    │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (2) 进入责任链 applyPreHandle
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────────┐
 │ McpInterceptorChain                                                                            │
 │                                                                                                │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 1: MetricsMcpInterceptor (Order: -100)                                         │ │
 │ │ 职责: 记录指标统计起始时间戳，更新无锁并发度量快照                                            │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (继续向下)                                    │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 2: AuditMcpInterceptor (Order: -50)  <-- 【TASK-007 新增核心拦截器】            │ │
 │ │ 职责: 捕获调用起始与上下文，准备审计记录；位于外层，全面覆盖成功、阻断与异常                 │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (继续向下)                                    │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 3: ToolAclMcpInterceptor (Order: 100)                                          │ │
 │ │ 职责: OWASP MCP02 权限治理 (白名单 / 黑名单判定，阻断抛出 McpAccessDeniedException)        │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (放行)                                        │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 4: ParameterValidationMcpInterceptor (Order: 200)                              │ │
 │ │ 职责: 必填项与防注入/路径遍历校验，违规抛出 McpGovernanceException                           │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (放行)                                        │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 5: LoggingMcpInterceptor (Order: 1000)                                         │ │
 │ │ 职责: 控制台格式化文本日志打印（纯文本摘要）                                                │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 └────────────────────────────────────────────────┼───────────────────────────────────────────────┘
                                                  │ (3) 策略放行，执行底层真实工具
                                                  ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ 真实业务 Tool 执行 (ToolExecutionCallback)                             │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (4) 执行完毕 (产出结果) 或发生异常
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────────┐
 │ 后置处理或异常处理链路                                                                         │
 │                                                                                                │
 │ 场景 A (成功):                                                                                 │
 │ 逆序执行 postHandle: Logging -> ParamValidation -> ToolAcl -> AuditMcpInterceptor              │
 │ AuditMcpInterceptor: 构建状态为 SUCCESS 的 McpAuditEvent (入参敏感脱敏)                         │
 │                                                                                                │
 │ 场景 B (阻断/异常):                                                                            │
 │ 抛出异常触发 chain.applyOnError:                                                               │
 │ AuditMcpInterceptor.onError: 捕获异常类型，映射为 ACCESS_DENIED / VALIDATION_FAILED / ERROR， │
 │ 提取阻断原因，构建不可变 McpAuditEvent                                                          │
 └───────────────────────────────────┬────────────────────────────────────────────────────────────┘
                                     │ (5) 触发事件发布
                                     ▼
                   ┌─────────────────────────────────────┐
                   │ McpAuditPublisher (SPI 契约)         │
                   └─────────┬─────────────────┬─────────┘
                             │                 │
                             ▼                 ▼
           ┌───────────────────────────┐     ┌──────────────────────────────────────────────┐
           │ DefaultMcpAuditPublisher  │     │ SpringMcpAuditPublisher (Starter 自动桥接)   │
           │ (纯 Java 17 内存事件广播) │     │ (发布 SpringMcpAuditEvent，支持 @EventListener)│
           └─────────────┬─────────────┘     └──────────────────────┬───────────────────────┘
                         │                                          │
                         ▼                                          ▼
     ┌───────────────────────────────────────┐   ┌──────────────────────────────────────────┐
     │ 纯 Java 自定义 McpAuditListener       │   │ JsonLoggingMcpAuditListener (结构化导出)  │
     │ (业务进程内即时监听、自定义管道集成)  │   │ (紧凑单行 JSON 写入 mcp-audit.log)       │
     └───────────────────────────────────────┘   └──────────────────────────────────────────┘
```

---

## 2. 核心类、包结构与详细设计

### 2.1 纯 Java 审计事件与发布监听模型 (`mcp-governance-core`)
包路径：`io.github.chukui2005.mcpgov.core.runtime.audit`

#### 1. `SensitiveDataMasker.java` (敏感数据脱敏工具)
纯 Java 静态工具类：
- 内置敏感键名特征集合（默认包含：`password`, `secret`, `token`, `apikey`, `api_key`, `authorization`, `privatekey`, `private_key`，不区分大小写）；
- 核心方法：`Map<String, Object> mask(Map<String, Object> source)`，深度递归处理嵌套 Map 与 List，将敏感字段值替换为安全掩码 `******`；非敏感字段完整保留；返回全新的不可变只读 Map。

#### 2. `McpAuditEvent.java` (不可变结构化审计事件记录)
使用 Java 17 `record` 建立领域模型：
```java
package io.github.chukui2005.mcpgov.core.runtime.audit;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record McpAuditEvent(
    String eventId,
    Instant timestamp,
    String traceId,
    String callerId,
    String toolName,
    Map<String, Object> arguments,
    InvocationStatus status,
    long durationMs,
    String failureReason,
    Map<String, Object> attributes
) {
    public McpAuditEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }
}
```

#### 3. `McpAuditListener.java` (审计事件监听器契约)
```java
package io.github.chukui2005.mcpgov.core.runtime.audit;

@FunctionalInterface
public interface McpAuditListener {
    /**
     * 处理结构化运行时审计事件。
     *
     * @param event 不可变审计事件记录
     */
    void onAuditEvent(McpAuditEvent event);
}
```

#### 4. `McpAuditPublisher.java` (审计事件发布器契约)
```java
package io.github.chukui2005.mcpgov.core.runtime.audit;

public interface McpAuditPublisher {
    /**
     * 发布一个结构化审计事件。
     *
     * @param event 审计事件
     */
    void publish(McpAuditEvent event);

    /**
     * 注册审计监听器。
     *
     * @param listener 监听器
     */
    void addListener(McpAuditListener listener);
}
```

#### 5. `DefaultMcpAuditPublisher.java` (纯 Java 并发安全默认发布器)
- 内部使用 `CopyOnWriteArrayList<McpAuditListener>` 存储已注册监听器；
- 在 `publish(McpAuditEvent event)` 中遍历监听器执行通知；
- **异常沙箱保护机制**：使用 `try-catch` 包裹单个监听器的调用。若某个监听器抛出任何未捕获异常，仅记录告警日志，绝不抛出阻断主流程，保证治理链路高可用。

#### 6. `AuditMcpInterceptor.java` (审计核心拦截器)
- 实现 `McpInterceptor`，固定 `getOrder() = -50`；
- 持有 `McpAuditPublisher` 与 `maskSensitiveArguments` 开关；
- **生命周期实现**：
  - `preHandle`: 记录起始时间戳，从上下文 attributes 提取或生成 traceId、callerId；
  - `postHandle`: 提取已脱敏入参（调用 `SensitiveDataMasker.mask`），状态置为 `InvocationStatus.SUCCESS`，构建并调用 `publisher.publish(...)`；
  - `onError`: 识别异常类别：
    - `McpAccessDeniedException` $\rightarrow$ `InvocationStatus.ACCESS_DENIED`；
    - `McpGovernanceException` $\rightarrow$ `InvocationStatus.VALIDATION_FAILED`；
    - 其他异常 $\rightarrow$ `InvocationStatus.ERROR`；
    - 提取脱敏入参和错误信息，构建并调用 `publisher.publish(...)`。

---

## 3. Spring Boot Starter 桥接与结构化 JSON 导出 (`mcp-governance-spring-boot-starter`)

### 3.1 `SpringMcpAuditEvent.java`
- 继承 `org.springframework.context.ApplicationEvent`；
- 包装核心不可变 `McpAuditEvent`，便于 Spring 宿主应用使用标准 `@EventListener` 或 `@TransactionalEventListener` 消费。

### 3.2 `SpringMcpAuditPublisher.java`
- 实现 `McpAuditPublisher` 并实现 `ApplicationEventPublisherAware`；
- 兼备纯 Java `McpAuditListener` 分发与 Spring `ApplicationEventPublisher.publishEvent` 广播双重能力。

### 3.3 `JsonLoggingMcpAuditListener.java` (结构化 JSON 日志导出)
- 实现 `McpAuditListener`；
- 构造注入 `ObjectMapper`（若 Spring 容器存在则自动注入，否则使用默认实例）；
- 将 `McpAuditEvent` 转换为单行紧凑 JSON 格式；
- 使用专属 Logger（`io.github.chukui2005.mcpgov.audit`）按 `INFO` 级别输出，满足 ELK/SIEM 原生采集需求。

### 3.4 配置属性绑定与自动装配
- 在 `McpGovernanceProperties` 扩展 `AuditProperties`：
  - `mcp.governance.audit.enabled` (默认 true)
  - `mcp.governance.audit.mask-sensitive-arguments` (默认 true)
  - `mcp.governance.audit.json-export.enabled` (默认 true)
  - `mcp.governance.audit.json-export.logger-name` (默认 "io.github.chukui2005.mcpgov.audit")
  - `mcp.governance.audit.spring-event.enabled` (默认 true)
- 在 `McpGovernanceAutoConfiguration` 中：
  - 自动注册 `McpAuditPublisher`，并自动装配容器中所有 `McpAuditListener`；
  - 条件装配 `JsonLoggingMcpAuditListener`；
  - 条件装配 `AuditMcpInterceptor` (Order: -50)。

---

## 4. 实施清单与工件计划 (Artifacts Checklist)

### 4.1 主要创建与修改文件
1. **架构与契约文档**（架构师已产出）：
   - `docs/adr/0007-runtime-audit-event-and-listener.md`
   - `docs/architecture/0007-blueprint.md`
   - `docs/contracts/audit-event.schema.json`
2. **核心层 (`mcp-governance-core`)**：
   - `SensitiveDataMasker.java`
   - `McpAuditEvent.java`
   - `McpAuditListener.java`
   - `McpAuditPublisher.java`
   - `DefaultMcpAuditPublisher.java`
   - `AuditMcpInterceptor.java`
3. **Starter 模块 (`mcp-governance-spring-boot-starter`)**：
   - `SpringMcpAuditEvent.java`
   - `SpringMcpAuditPublisher.java`
   - `JsonLoggingMcpAuditListener.java`
   - `McpGovernanceProperties.java`（新增 Audit 嵌套配置）
   - `McpGovernanceAutoConfiguration.java`（注入审计发布器、监听器与拦截器）
4. **测试套件**：
   - `SensitiveDataMaskerTest.java`（针对深层嵌套参数脱敏的全面测试）
   - `DefaultMcpAuditPublisherTest.java`（多监听器分发与异常隔离沙箱测试）
   - `AuditMcpInterceptorTest.java`（成功调用、ACL 阻断、参数校验违规全生命周期审计事件测试）
   - `JsonLoggingMcpAuditListenerTest.java`（JSON 结构化输出格式验证与 Jackson 序列化测试）
   - `SpringMcpAuditIntegrationTest.java`（Spring 上下文 `@EventListener` 端到端集成测试）

---

## 5. 质量与安全门禁

1. **`core` 纯洁性**：严禁在 `mcp-governance-core` 中引入 Spring 框架类；
2. **OWASP MCP01 / MCP08 对齐**：审计事件入参必须强制脱敏，杜绝 Secret 泄露；
3. **CI 验收标准**：`mvn -B verify` 100% 通过，`mvn javadoc:javadoc` 零警告，所有新接口具备中文文档注释。

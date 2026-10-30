# 0006. TASK-006 技术实施蓝图：运行时治理指标遥测与 Spring Boot Actuator 端点集成

## 1. 架构总览与全链路遥测流转

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │ 外部调用者 / Agent / 大模型                                               │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (1) 调用工具: toolName, arguments, attributes
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ McpGovernanceAdapter (端到端统一治理适配器)                              │
 │ 构造初始 McpInvocationContext                                           │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (2) 进入责任链 applyPreHandle
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────────┐
 │ McpInterceptorChain                                                                            │
 │                                                                                                │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 1: MetricsMcpInterceptor (Order: -100)                                         │ │
 │ │ 职责: 记录调用起始时间戳进入 context.attributes，处于拦截链最外层包裹位置                    │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (继续向下)                                    │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 2: ToolAclMcpInterceptor (Order: 100)                                          │ │
 │ │ 职责: OWASP MCP02 权限治理 (白名单 / 黑名单判定)                                             │ │
 │ │ 失败: 抛出 McpAccessDeniedException                                                        │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (放行)                                        │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 3: ParameterValidationMcpInterceptor (Order: 200)                              │ │
 │ │ 职责: 必填项与防注入/路径遍历正则校验                                                        │ │
 │ │ 失败: 抛出 McpGovernanceException                                                         │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 │                                                │ (放行)                                        │
 │                                                ▼                                               │
 │ ┌────────────────────────────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 4: LoggingMcpInterceptor (Order: 1000)                                         │ │
 │ │ 职责: 敏感参数掩码脱敏与审计日志输出                                                          │ │
 │ └──────────────────────────────────────────────┬─────────────────────────────────────────────┘ │
 └────────────────────────────────────────────────┼───────────────────────────────────────────────┘
                                                  │ (3) 策略全部放行，执行底层真实工具
                                                  ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ 真实业务 Tool 执行 (ToolExecutionCallback)                             │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (4) 执行完毕，产出结果或抛出异常
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────────┐
 │ 后置处理或异常处理                                                                               │
 │                                                                                                │
 │ 情况 A (成功):                                                                                 │
 │ 逆序执行 postHandle: Logging -> ParamValidation -> ToolAcl -> Metrics                         │
 │ MetricsMcpInterceptor.postHandle: 统计耗时，记录 SUCCESS 指标                                   │
 │                                                                                                │
 │ 情况 B (阻断/异常):                                                                             │
 │ 发生异常抛出，进入 chain.applyOnError(governedContext, ex)                                     │
 │ MetricsMcpInterceptor.onError: 捕获异常类型，精准映射为 ACCESS_DENIED / VALIDATION_FAILED /   │
 │ ERROR，统计耗时并累计阻断计数                                                                   │
 └───────────────────────────────────┬────────────────────────────────────────────────────────────┘
                                     │ (5) 同步更新指标存储
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ McpMetricsCollector (SPI)           │
                  └─────────┬─────────────────┬─────────┘
                            │                 │
                            ▼                 ▼
          ┌───────────────────────────┐     ┌──────────────────────────────────────────────┐
          │ InMemoryMcpMetricsCollector│    │ MicrometerMcpMetricsCollector (Starter 条件装配)│
          │ (纯 Java 17 无锁原子计数)  │    │ (绑定 MeterRegistry Counter / Timer)         │
          └─────────────┬─────────────┘     └──────────────────────┬───────────────────────┘
                        │                                          │
                        ▼                                          ▼
          ┌───────────────────────────┐     ┌──────────────────────────────────────────────┐
          │ Actuator 监控端点          │    │ Prometheus / Grafana 指标导出通道            │
          │ (/actuator/mcpGovernance) │    │ (mcp.governance.invocations / execution.time)│
          └───────────────────────────┘     └──────────────────────────────────────────────┘
```

---

## 2. 核心类、包结构与接口详细设计

### 2.1 纯 Java 遥测领域模型 (`mcp-governance-core`)
包路径：`io.github.chukui2005.mcpgov.core.runtime.metrics`

#### 1. `InvocationStatus.java`
```java
package io.github.chukui2005.mcpgov.core.runtime.metrics;

/**
 * MCP 工具调用治理结果状态枚举。
 */
public enum InvocationStatus {
    /** 调用成功完成。 */
    SUCCESS,
    /** 因命中 Tool ACL 权限策略被阻断（OWASP MCP02）。 */
    ACCESS_DENIED,
    /** 因未通过入参安全校验被阻断（OWASP MCP05）。 */
    VALIDATION_FAILED,
    /** 工具内部执行异常或系统未捕获错误。 */
    ERROR
}
```

#### 2. `ToolMetricsSnapshot.java` (单个工具度量快照)
```java
package io.github.chukui2005.mcpgov.core.runtime.metrics;

/**
 * 单个 MCP 工具的不可变运行时度量快照。
 */
public record ToolMetricsSnapshot(
    String toolName,
    long totalInvocations,
    long successCount,
    long accessDeniedCount,
    long validationFailedCount,
    long errorCount,
    long totalDurationMs,
    long maxDurationMs,
    double averageDurationMs,
    String lastFailureReason
) {
}
```

#### 3. `McpMetricsSnapshot.java` (全局治理快照)
```java
package io.github.chukui2005.mcpgov.core.runtime.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * 全局 MCP 运行时治理指标快照。
 */
public record McpMetricsSnapshot(
    Instant timestamp,
    long totalInvocations,
    long totalSuccess,
    long totalAccessDenied,
    long totalValidationFailed,
    long totalError,
    double overallSuccessRate,
    double overallDeniedRate,
    Map<String, ToolMetricsSnapshot> toolMetrics
) {
}
```

#### 4. `McpMetricsCollector.java` (指标收集器 SPI)
```java
package io.github.chukui2005.mcpgov.core.runtime.metrics;

/**
 * MCP 运行时指标收集器 SPI 契约（纯 Java 17，无外部框架依赖）。
 */
public interface McpMetricsCollector {

    /**
     * 记录一次工具调用的治理与性能度量事件。
     *
     * @param toolName 工具名称
     * @param durationMs 执行耗时（毫秒）
     * @param status 调用结果状态
     * @param failureReason 失败或阻断原因（成功时为 null）
     */
    void record(String toolName, long durationMs, InvocationStatus status, String failureReason);

    /**
     * 获取当前系统瞬时治理度量快照。
     *
     * @return 不可变指标快照
     */
    McpMetricsSnapshot getSnapshot();

    /**
     * 重置所有度量统计数据（主要用于测试或定期滚动）。
     */
    void reset();
}
```

#### 5. `InMemoryMcpMetricsCollector.java` (默认纯 Java 无锁实现)
- 内部针对全局和各工具分别使用无锁计数器维护：
  - `LongAdder totalInvocations = new LongAdder();`
  - `LongAdder successCount = new LongAdder();`
  - `LongAdder accessDeniedCount = new LongAdder();`
  - `LongAdder validationFailedCount = new LongAdder();`
  - `LongAdder errorCount = new LongAdder();`
  - `LongAdder totalDurationMs = new LongAdder();`
  - `AtomicLong maxDurationMs = new AtomicLong(0);`
- 使用 `ConcurrentHashMap<String, ToolCounter>` 存储各工具计数；
- 在 `getSnapshot()` 中计算衍生指标（平均耗时、成功率、阻断率等），返回不可变的 `McpMetricsSnapshot`。

#### 6. `MetricsMcpInterceptor.java` (指标收集拦截器)
- 位于包：`io.github.chukui2005.mcpgov.core.runtime.metrics`
- 实现 `McpInterceptor`；
- 成员变量：`private final McpMetricsCollector collector;`
- **优先级**：`getOrder()` 返回 `-100`（早于 ACL 的 `100`，处于最外层）；
- **上下文属性 Key**：`public static final String ATTR_START_TIME_MS = "_mcp_metrics_start_time_ms";`
- **逻辑实现**：
  - `preHandle`:
    ```java
    Map<String, Object> newAttrs = new HashMap<>(context.attributes());
    newAttrs.put(ATTR_START_TIME_MS, System.currentTimeMillis());
    return new McpInvocationContext(context.toolName(), context.arguments(), newAttrs, context.timestamp());
    ```
  - `postHandle`:
    ```java
    Long start = (Long) context.attributes().get(ATTR_START_TIME_MS);
    long duration = (start != null) ? (System.currentTimeMillis() - start) : result.durationMs();
    collector.record(context.toolName(), duration, InvocationStatus.SUCCESS, null);
    return result;
    ```
  - `onError`:
    ```java
    Long start = (Long) context.attributes().get(ATTR_START_TIME_MS);
    long duration = (start != null) ? (System.currentTimeMillis() - start) : 0L;
    InvocationStatus status;
    if (throwable instanceof McpAccessDeniedException) {
        status = InvocationStatus.ACCESS_DENIED;
    } else if (throwable instanceof McpGovernanceException) {
        status = InvocationStatus.VALIDATION_FAILED;
    } else {
        status = InvocationStatus.ERROR;
    }
    collector.record(context.toolName(), duration, status, throwable.getMessage());
    ```

---

### 2.2 Spring Boot Starter 模块集成 (`mcp-governance-spring-boot-starter`)

#### 1. Maven 依赖管理 (`pom.xml`)
引入可选依赖：
```xml
<!-- Micrometer 核心抽象（可选） -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <optional>true</optional>
</dependency>

<!-- Spring Boot Actuator（可选） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-actuator</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-actuator-autoconfigure</artifactId>
    <optional>true</optional>
</dependency>
```

#### 2. 配置扩展 (`McpGovernanceProperties.java`)
新增 `MetricsProperties`：
```java
private MetricsProperties metrics = new MetricsProperties();

public static class MetricsProperties {
    private boolean enabled = true;
    private boolean micrometerEnabled = true;
    private boolean exportToActuator = true;

    // getters and setters with Javadoc
}
```

#### 3. Micrometer 适配收集器 (`MicrometerMcpMetricsCollector.java`)
包路径：`io.github.chukui2005.mcpgov.spring.boot.metrics`
- 实现 `McpMetricsCollector` 接口；
- 持有 `MeterRegistry registry` 与代理委托 `InMemoryMcpMetricsCollector delegate`；
- 在 `record()` 中：
  1. 调用 `delegate.record(toolName, durationMs, status, failureReason)`（保证内存快照始终可用）；
  2. 利用 Micrometer `Counter.builder("mcp.governance.invocations")` 记录标签 `tool`, `status`；
  3. 若 `status == SUCCESS`，使用 `Timer.builder("mcp.governance.execution.time")` 记录耗时；
  4. 若 `status == ACCESS_DENIED || status == VALIDATION_FAILED`，递增 `Counter.builder("mcp.governance.denied.total")`。
- `getSnapshot()` 与 `reset()` 直接委托给 `delegate`。

#### 4. Actuator 监控端点 (`McpGovernanceEndpoint.java`)
包路径：`io.github.chukui2005.mcpgov.spring.boot.actuator`
- 类注解：
  ```java
  @Endpoint(id = "mcpGovernance")
  public class McpGovernanceEndpoint {
      private final McpMetricsCollector collector;
      private final Instant startupTime = Instant.now();

      public McpGovernanceEndpoint(McpMetricsCollector collector) {
          this.collector = collector;
      }

      @ReadOperation
      public Map<String, Object> getGovernanceMetrics() {
          McpMetricsSnapshot snapshot = collector.getSnapshot();
          Map<String, Object> data = new LinkedHashMap<>();
          data.put("status", "UP");
          data.put("uptimeMs", Duration.between(startupTime, Instant.now()).toMillis());
          data.put("timestamp", snapshot.timestamp().toString());
          data.put("summary", Map.of(
              "totalInvocations", snapshot.totalInvocations(),
              "totalSuccess", snapshot.totalSuccess(),
              "totalAccessDenied", snapshot.totalAccessDenied(),
              "totalValidationFailed", snapshot.totalValidationFailed(),
              "totalError", snapshot.totalError(),
              "successRate", snapshot.overallSuccessRate(),
              "deniedRate", snapshot.overallDeniedRate()
          ));
          data.put("tools", snapshot.toolMetrics());
          return data;
      }
  }
  ```

#### 5. 自动装配更新 (`McpGovernanceAutoConfiguration.java`)
- 注册 `McpMetricsCollector` Bean：
  ```java
  @Bean
  @ConditionalOnMissingBean(McpMetricsCollector.class)
  @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
  public McpMetricsCollector mcpMetricsCollector(
          McpGovernanceProperties properties,
          ObjectProvider<MeterRegistry> meterRegistryProvider) {
      MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
      boolean useMicrometer = properties.getMetrics().isMicrometerEnabled() && meterRegistry != null;
      if (useMicrometer) {
          return new MicrometerMcpMetricsCollector(meterRegistry);
      }
      return new InMemoryMcpMetricsCollector();
  }
  ```
- 注册 `MetricsMcpInterceptor` Bean：
  ```java
  @Bean
  @ConditionalOnMissingBean(MetricsMcpInterceptor.class)
  @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
  public MetricsMcpInterceptor metricsMcpInterceptor(McpMetricsCollector collector) {
      return new MetricsMcpInterceptor(collector);
  }
  ```
- 注册 `McpGovernanceEndpoint` Bean：
  ```java
  @Bean
  @ConditionalOnClass(Endpoint.class)
  @ConditionalOnMissingBean(McpGovernanceEndpoint.class)
  @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "export-to-actuator", havingValue = "true", matchIfMissing = true)
  public McpGovernanceEndpoint mcpGovernanceEndpoint(McpMetricsCollector collector) {
      return new McpGovernanceEndpoint(collector);
  }
  ```

---

## 3. 研发工程师实施路径与文件清单

### 3.1 主要修改与新增文件清单

1. **`mcp-governance-core`（纯 Java 17，零外部依赖）**：
   - 新增包 `io.github.chukui2005.mcpgov.core.runtime.metrics`：
     - `InvocationStatus.java`（枚举：调用状态）
     - `ToolMetricsSnapshot.java`（Record：工具指标快照）
     - `McpMetricsSnapshot.java`（Record：系统指标快照）
     - `McpMetricsCollector.java`（接口：收集器 SPI 契约）
     - `InMemoryMcpMetricsCollector.java`（实现：高性能无锁并发收集器）
     - `MetricsMcpInterceptor.java`（实现：拦截器，Order: -100）
   - 单测套件：
     - `InMemoryMcpMetricsCollectorTest.java`（多线程高并发计数测试、快照准确性验证、重置功能测试）
     - `MetricsMcpInterceptorTest.java`（成功调用埋点、ACL 阻断被捕获、参数校验异常被捕获、耗时统计测试）

2. **`mcp-governance-spring-boot-starter`（条件装配与生态集成）**：
   - 修改 `pom.xml`：引入 `micrometer-core`、`spring-boot-actuator` 为 optional；
   - 修改 `McpGovernanceProperties.java`：增加 `MetricsProperties` 嵌套配置及完整 Javadoc；
   - 新增包 `io.github.chukui2005.mcpgov.spring.boot.metrics`：
     - `MicrometerMcpMetricsCollector.java`（实现：Micrometer 桥接收集器）
   - 新增包 `io.github.chukui2005.mcpgov.spring.boot.actuator`：
     - `McpGovernanceEndpoint.java`（实现：Actuator @Endpoint）
   - 修改 `McpGovernanceAutoConfiguration.java`：装配收集器、拦截器与端点 Bean；
   - 单测套件：
     - `MicrometerMcpMetricsCollectorTest.java`（验证在 SimpleMeterRegistry 下 counter/timer 正常增加）
     - `McpGovernanceEndpointTest.java`（验证端点读取的 JSON 结构与指标一致）
     - `McpGovernanceMetricsAutoConfigurationTest.java`（验证在有/无 MeterRegistry、有/无 Actuator 时的优雅降级装配）

### 3.2 遵循的既有设计模式
- **不可变上下文与快照模式**：`McpMetricsSnapshot` 与 `ToolMetricsSnapshot` 使用 Java 17 Record 实现不可变数据载体；
- **SPI 契约与分层隔离**：`mcp-governance-core` 仅定义纯 Java `McpMetricsCollector` 接口与基础实现；
- **Spring Boot 自动装配规范**：所有新增 Bean 必须带有 `@ConditionalOnMissingBean`，配置项必须绑定 `@ConfigurationProperties` 并提供清晰 Javadoc。

### 3.3 编码与执行顺序建议
1. 编写 `core` 模块指标枚举、Record、SPI 接口；
2. 编写 `core` 模块无锁实现与拦截器；
3. 编写 `core` 模块单元测试并运行验证通过；
4. 扩展 `starter` 模块 `pom.xml` 与配置属性；
5. 编写 `starter` 模块 Micrometer 适配与 Actuator 端点；
6. 扩展自动装配类并编写集成测试；
7. 执行全模块 `mvn -B verify` 与 `mvn -B javadoc:javadoc` 确保零告警通过。

---

## 4. 验证与测试验收清单

| 测试类 | 验证目标 | 预期结果 |
| :--- | :--- | :--- |
| `InMemoryMcpMetricsCollectorTest` | 并发调用累加准确性 | 100 个并发线程同时调用 `record`，最终总数与各状态精确一致，无丢数 |
| `MetricsMcpInterceptorTest` | 成功请求埋点 | 后置处理记录一次 `SUCCESS`，耗时 $\ge 0$ |
| `MetricsMcpInterceptorTest` | Tool ACL 阻断拦截埋点 | 抛出 `McpAccessDeniedException` 时，计数器精准记录 `ACCESS_DENIED` |
| `MetricsMcpInterceptorTest` | 参数校验失败拦截埋点 | 抛出 `McpGovernanceException` 时，计数器精准记录 `VALIDATION_FAILED` |
| `MicrometerMcpMetricsCollectorTest`| Micrometer 注册表集成 | 触发调用后，`registry.find("mcp.governance.invocations")` 成功检索到指标 |
| `McpGovernanceEndpointTest` | Actuator 端点暴露 | 调用 `@ReadOperation`，返回状态 `UP`、摘要与工具维度明细，字段完整 |
| `McpGovernanceMetricsAutoConfigurationTest` | 降级验证 | 当未配置 `MeterRegistry` 时，自动优雅降级为 `InMemoryMcpMetricsCollector` |

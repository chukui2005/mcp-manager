package io.github.chukui2005.mcpgov.spring.boot.metrics;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * 桥接 Micrometer 监控体系的指标收集器适配器。
 * 同时代理委托 {@link InMemoryMcpMetricsCollector}，确保 Actuator 端点查询与 Prometheus 抓取数据双向一致。
 */
public class MicrometerMcpMetricsCollector implements McpMetricsCollector {

    private static final String METRIC_INVOCATIONS = "mcp.governance.invocations";
    private static final String METRIC_EXECUTION_TIME = "mcp.governance.execution.time";
    private static final String METRIC_DENIED_TOTAL = "mcp.governance.denied.total";

    private final MeterRegistry registry;
    private final InMemoryMcpMetricsCollector delegate;

    /**
     * 构造 Micrometer 指标收集器。
     *
     * @param registry Micrometer 注册表，非空
     */
    public MicrometerMcpMetricsCollector(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry 不能为 null");
        this.delegate = new InMemoryMcpMetricsCollector();
    }

    @Override
    public void record(String toolName, long durationMs, InvocationStatus status, String failureReason) {
        // 1. 委托内存收集器记录
        delegate.record(toolName, durationMs, status, failureReason);

        // 2. 记录 Micrometer 调用总数 Counter
        Counter.builder(METRIC_INVOCATIONS)
                .description("MCP 工具调用总次数统计")
                .tag("tool", toolName)
                .tag("status", status.name())
                .register(registry)
                .increment();

        // 3. 成功时记录执行耗时 Timer
        if (status == InvocationStatus.SUCCESS) {
            Timer.builder(METRIC_EXECUTION_TIME)
                    .description("MCP 工具调用成功执行耗时分布")
                    .tag("tool", toolName)
                    .register(registry)
                    .record(Duration.ofMillis(Math.max(0L, durationMs)));
        }

        // 4. 发生策略阻断或校验失败时记录专有计数器
        if (status == InvocationStatus.ACCESS_DENIED) {
            Counter.builder(METRIC_DENIED_TOTAL)
                    .description("MCP 治理安全阻断总次数")
                    .tag("tool", toolName)
                    .tag("type", "ACL")
                    .register(registry)
                    .increment();
        } else if (status == InvocationStatus.VALIDATION_FAILED) {
            Counter.builder(METRIC_DENIED_TOTAL)
                    .description("MCP 治理安全阻断总次数")
                    .tag("tool", toolName)
                    .tag("type", "VALIDATION")
                    .register(registry)
                    .increment();
        }
    }

    @Override
    public McpMetricsSnapshot getSnapshot() {
        return delegate.getSnapshot();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    /**
     * 获取底层的 MeterRegistry。
     *
     * @return 注册表实例
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}

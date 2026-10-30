package io.github.chukui2005.mcpgov.spring.boot.metrics;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer 指标适配器集成单元测试。
 */
class MicrometerMcpMetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private MicrometerMcpMetricsCollector collector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new MicrometerMcpMetricsCollector(meterRegistry);
    }

    @Test
    @DisplayName("测试成功调用时的 Counter 与 Timer 记录")
    void testRecordSuccessMetrics() {
        collector.record("weather_tool", 120, InvocationStatus.SUCCESS, null);

        // 校验内存快照
        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertThat(snapshot.totalInvocations()).isEqualTo(1);
        assertThat(snapshot.totalSuccess()).isEqualTo(1);

        // 校验 Micrometer Counter
        Counter invocationCounter = meterRegistry.find("mcp.governance.invocations")
                .tag("tool", "weather_tool")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(invocationCounter).isNotNull();
        assertThat(invocationCounter.count()).isEqualTo(1.0);

        // 校验 Micrometer Timer
        Timer timer = meterRegistry.find("mcp.governance.execution.time")
                .tag("tool", "weather_tool")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0);
    }

    @Test
    @DisplayName("测试 ACL 阻断与参数校验失败时的专有 Counter 记录")
    void testRecordDeniedMetrics() {
        collector.record("admin_tool", 20, InvocationStatus.ACCESS_DENIED, "Tool ACL 阻断");
        collector.record("admin_tool", 15, InvocationStatus.VALIDATION_FAILED, "参数正则不匹配");

        Counter aclDeniedCounter = meterRegistry.find("mcp.governance.denied.total")
                .tag("tool", "admin_tool")
                .tag("type", "ACL")
                .counter();
        assertThat(aclDeniedCounter).isNotNull();
        assertThat(aclDeniedCounter.count()).isEqualTo(1.0);

        Counter validationDeniedCounter = meterRegistry.find("mcp.governance.denied.total")
                .tag("tool", "admin_tool")
                .tag("type", "VALIDATION")
                .counter();
        assertThat(validationDeniedCounter).isNotNull();
        assertThat(validationDeniedCounter.count()).isEqualTo(1.0);
    }
}

package io.github.chukui2005.mcpgov.spring.boot.actuator;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot Actuator 端点单元测试。
 */
class McpGovernanceEndpointTest {

    private InMemoryMcpMetricsCollector collector;
    private McpGovernanceEndpoint endpoint;

    @BeforeEach
    void setUp() {
        collector = new InMemoryMcpMetricsCollector();
        endpoint = new McpGovernanceEndpoint(collector);
    }

    @Test
    @DisplayName("测试端点读取返回的治理 JSON 数据结构完整性")
    void testGetGovernanceMetrics() {
        collector.record("queryDb", 80, InvocationStatus.SUCCESS, null);
        collector.record("deleteDb", 10, InvocationStatus.ACCESS_DENIED, "Tool ACL 阻断");

        Map<String, Object> metrics = endpoint.getGovernanceMetrics();

        assertThat(metrics).containsKeys("status", "uptimeMs", "timestamp", "summary", "tools");
        assertThat(metrics.get("status")).isEqualTo("UP");
        assertThat(((Number) metrics.get("uptimeMs")).longValue()).isGreaterThanOrEqualTo(0L);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) metrics.get("summary");
        assertThat(summary.get("totalInvocations")).isEqualTo(2L);
        assertThat(summary.get("totalSuccess")).isEqualTo(1L);
        assertThat(summary.get("totalAccessDenied")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> tools = (Map<String, Object>) metrics.get("tools");
        assertThat(tools).containsKeys("queryDb", "deleteDb");
    }
}

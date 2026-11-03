package io.github.chukui2005.mcpgovernance.gateway.actuator;

import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsSnapshot;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 网关独立的 Spring Boot Actuator 治理度量端点。
 * 暴露端点：{@code /actuator/mcpGovernance}。
 */
@Component
@Endpoint(id = "mcpGovernance")
public class GatewayGovernanceEndpoint {

    private final McpMetricsCollector collector;
    private final Instant startupTime = Instant.now();

    /**
     * 构造网关监控端点。
     *
     * @param collector 运行时指标收集器，非空
     */
    public GatewayGovernanceEndpoint(McpMetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector 不能为 null");
    }

    /**
     * 读取网关实时治理指标数据。
     *
     * @return 监控数据映射表
     */
    @ReadOperation
    public Map<String, Object> getGovernanceMetrics() {
        McpMetricsSnapshot snapshot = collector.getSnapshot();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("uptimeMs", Math.max(0L, Duration.between(startupTime, Instant.now()).toMillis()));
        data.put("timestamp", snapshot.timestamp().toString());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalInvocations", snapshot.totalInvocations());
        summary.put("totalSuccess", snapshot.totalSuccess());
        summary.put("totalAccessDenied", snapshot.totalAccessDenied());
        summary.put("totalValidationFailed", snapshot.totalValidationFailed());
        summary.put("totalError", snapshot.totalError());
        summary.put("successRate", snapshot.overallSuccessRate());
        summary.put("deniedRate", snapshot.overallDeniedRate());
        data.put("summary", summary);

        data.put("tools", snapshot.toolMetrics());
        return data;
    }
}

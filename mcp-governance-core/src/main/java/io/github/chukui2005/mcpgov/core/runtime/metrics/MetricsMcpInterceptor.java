package io.github.chukui2005.mcpgov.core.runtime.metrics;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;

import java.util.Objects;

/**
 * MCP 运行时治理指标收集拦截器。
 * 位于责任链最外层包裹位置（Order: -100），确保全链路覆盖正常调用、ACL 阻断与参数校验失败。
 */
public class MetricsMcpInterceptor implements McpInterceptor {

    /**
     * 上下文属性 Key：调用开始时间戳（毫秒）。
     */
    public static final String ATTR_START_TIME_MS = "_mcp_metrics_start_time_ms";

    private final McpMetricsCollector collector;

    /**
     * 构造治理指标收集拦截器。
     *
     * @param collector 指标收集器，非空
     */
    public MetricsMcpInterceptor(McpMetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "collector 不能为 null");
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public McpInvocationContext preHandle(McpInvocationContext context) {
        return context.withAttribute(ATTR_START_TIME_MS, System.currentTimeMillis());
    }

    @Override
    public McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
        long duration = calculateDuration(context, result != null ? result.durationMs() : 0L);
        collector.record(context.toolName(), duration, InvocationStatus.SUCCESS, null);
        return result;
    }

    @Override
    public void onError(McpInvocationContext context, Throwable throwable) {
        long duration = calculateDuration(context, 0L);
        InvocationStatus status;
        if (throwable instanceof McpAccessDeniedException) {
            status = InvocationStatus.ACCESS_DENIED;
        } else if (throwable instanceof McpGovernanceException) {
            status = InvocationStatus.VALIDATION_FAILED;
        } else {
            status = InvocationStatus.ERROR;
        }
        String failureReason = throwable != null ? throwable.getMessage() : "未知异常";
        collector.record(context.toolName(), duration, status, failureReason);
    }

    /**
     * 获取持有的指标收集器。
     *
     * @return 指标收集器实例
     */
    public McpMetricsCollector getCollector() {
        return collector;
    }

    private long calculateDuration(McpInvocationContext context, long fallbackDuration) {
        if (context != null && context.attributes() != null) {
            Object startTimeObj = context.attributes().get(ATTR_START_TIME_MS);
            if (startTimeObj instanceof Number num) {
                return Math.max(0L, System.currentTimeMillis() - num.longValue());
            }
        }
        return Math.max(0L, fallbackDuration);
    }
}

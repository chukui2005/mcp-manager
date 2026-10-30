package io.github.chukui2005.mcpgov.core.runtime.metrics;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 全局 MCP 运行时治理指标快照。
 *
 * @param timestamp 快照生成时间戳
 * @param totalInvocations 全局总调用次数
 * @param totalSuccess 全局成功调用次数
 * @param totalAccessDenied 全局 ACL 阻断次数
 * @param totalValidationFailed 全局参数校验失败次数
 * @param totalError 全局错误或异常次数
 * @param overallSuccessRate 全局成功率（0.0 ~ 1.0）
 * @param overallDeniedRate 全局阻断率（0.0 ~ 1.0，包含 ACL 阻断与校验失败）
 * @param toolMetrics 各工具的度量快照映射，只读视图
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
    /**
     * 规范构造函数，保证不可变性与安全性。
     */
    public McpMetricsSnapshot {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        toolMetrics = toolMetrics == null ? Map.of() : Collections.unmodifiableMap(toolMetrics);
    }
}

package io.github.chukui2005.mcpgov.core.runtime.metrics;

/**
 * 单个 MCP 工具的不可变运行时度量快照。
 *
 * @param toolName 工具名称
 * @param totalInvocations 工具总调用次数
 * @param successCount 成功调用次数
 * @param accessDeniedCount ACL 权限阻断次数
 * @param validationFailedCount 参数校验失败次数
 * @param errorCount 异常或错误次数
 * @param totalDurationMs 总调用耗时（毫秒）
 * @param maxDurationMs 单次最大耗时（毫秒）
 * @param averageDurationMs 平均调用耗时（毫秒）
 * @param lastFailureReason 最近一次失败或阻断原因（无失败时为 null）
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

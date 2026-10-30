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

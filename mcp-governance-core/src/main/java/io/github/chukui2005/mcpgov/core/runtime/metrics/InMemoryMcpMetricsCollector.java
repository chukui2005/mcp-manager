package io.github.chukui2005.mcpgov.core.runtime.metrics;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 基于 Java 原生并发原子类实现的高性能无锁内存指标收集器。
 * 纯 Java 17 实现，无外部框架依赖。
 */
public class InMemoryMcpMetricsCollector implements McpMetricsCollector {

    private final LongAdder globalTotalInvocations = new LongAdder();
    private final LongAdder globalSuccess = new LongAdder();
    private final LongAdder globalAccessDenied = new LongAdder();
    private final LongAdder globalValidationFailed = new LongAdder();
    private final LongAdder globalError = new LongAdder();

    private final ConcurrentHashMap<String, ToolMetricsCounter> toolCounters = new ConcurrentHashMap<>();

    @Override
    public void record(String toolName, long durationMs, InvocationStatus status, String failureReason) {
        Objects.requireNonNull(toolName, "toolName 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");

        long validDuration = Math.max(0L, durationMs);

        // 1. 全局累加
        globalTotalInvocations.increment();
        switch (status) {
            case SUCCESS -> globalSuccess.increment();
            case ACCESS_DENIED -> globalAccessDenied.increment();
            case VALIDATION_FAILED -> globalValidationFailed.increment();
            case ERROR -> globalError.increment();
        }

        // 2. 工具维度累加
        toolCounters.computeIfAbsent(toolName, ToolMetricsCounter::new)
                .record(validDuration, status, failureReason);
    }

    @Override
    public McpMetricsSnapshot getSnapshot() {
        long total = globalTotalInvocations.sum();
        long success = globalSuccess.sum();
        long accessDenied = globalAccessDenied.sum();
        long validationFailed = globalValidationFailed.sum();
        long error = globalError.sum();

        double successRate = total > 0 ? (double) success / total : 0.0;
        double deniedRate = total > 0 ? (double) (accessDenied + validationFailed) / total : 0.0;

        Map<String, ToolMetricsSnapshot> toolMap = new LinkedHashMap<>();
        toolCounters.forEach((name, counter) -> toolMap.put(name, counter.snapshot()));

        return new McpMetricsSnapshot(
                Instant.now(),
                total,
                success,
                accessDenied,
                validationFailed,
                error,
                successRate,
                deniedRate,
                Collections.unmodifiableMap(toolMap)
        );
    }

    @Override
    public void reset() {
        globalTotalInvocations.reset();
        globalSuccess.reset();
        globalAccessDenied.reset();
        globalValidationFailed.reset();
        globalError.reset();
        toolCounters.clear();
    }

    /**
     * 单个工具的并发计数器。
     */
    private static final class ToolMetricsCounter {
        private final String toolName;
        private final LongAdder total = new LongAdder();
        private final LongAdder success = new LongAdder();
        private final LongAdder accessDenied = new LongAdder();
        private final LongAdder validationFailed = new LongAdder();
        private final LongAdder error = new LongAdder();
        private final LongAdder totalDuration = new LongAdder();
        private final AtomicLong maxDuration = new AtomicLong(0L);
        private final AtomicReference<String> lastFailureReason = new AtomicReference<>();

        private ToolMetricsCounter(String toolName) {
            this.toolName = toolName;
        }

        private void record(long durationMs, InvocationStatus status, String failureReason) {
            total.increment();
            totalDuration.add(durationMs);
            maxDuration.accumulateAndGet(durationMs, Math::max);

            switch (status) {
                case SUCCESS -> success.increment();
                case ACCESS_DENIED -> {
                    accessDenied.increment();
                    if (failureReason != null) {
                        lastFailureReason.set(failureReason);
                    }
                }
                case VALIDATION_FAILED -> {
                    validationFailed.increment();
                    if (failureReason != null) {
                        lastFailureReason.set(failureReason);
                    }
                }
                case ERROR -> {
                    error.increment();
                    if (failureReason != null) {
                        lastFailureReason.set(failureReason);
                    }
                }
            }
        }

        private ToolMetricsSnapshot snapshot() {
            long totalCount = total.sum();
            long successCount = success.sum();
            long deniedCount = accessDenied.sum();
            long valFailedCount = validationFailed.sum();
            long errCount = error.sum();
            long durSum = totalDuration.sum();
            long maxDur = maxDuration.get();
            double avgDur = totalCount > 0 ? (double) durSum / totalCount : 0.0;

            return new ToolMetricsSnapshot(
                    toolName,
                    totalCount,
                    successCount,
                    deniedCount,
                    valFailedCount,
                    errCount,
                    durSum,
                    maxDur,
                    avgDur,
                    lastFailureReason.get()
            );
        }
    }
}

package io.github.chukui2005.mcpgov.core.runtime.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯 Java 内存指标收集器单元测试。
 */
class InMemoryMcpMetricsCollectorTest {

    private InMemoryMcpMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new InMemoryMcpMetricsCollector();
    }

    @Test
    @DisplayName("测试基础指标记录与快照聚合")
    void testBasicRecordAndSnapshot() {
        collector.record("readFile", 100, InvocationStatus.SUCCESS, null);
        collector.record("readFile", 200, InvocationStatus.SUCCESS, null);
        collector.record("writeFile", 50, InvocationStatus.ACCESS_DENIED, "Tool ACL 阻断");
        collector.record("executeCommand", 30, InvocationStatus.VALIDATION_FAILED, "参数正则校验失败");
        collector.record("fetchUrl", 500, InvocationStatus.ERROR, "连接超时");

        McpMetricsSnapshot snapshot = collector.getSnapshot();

        assertEquals(5, snapshot.totalInvocations());
        assertEquals(2, snapshot.totalSuccess());
        assertEquals(1, snapshot.totalAccessDenied());
        assertEquals(1, snapshot.totalValidationFailed());
        assertEquals(1, snapshot.totalError());
        assertEquals(2.0 / 5.0, snapshot.overallSuccessRate(), 0.001);
        assertEquals(2.0 / 5.0, snapshot.overallDeniedRate(), 0.001);

        ToolMetricsSnapshot readSnapshot = snapshot.toolMetrics().get("readFile");
        assertNotNull(readSnapshot);
        assertEquals(2, readSnapshot.totalInvocations());
        assertEquals(2, readSnapshot.successCount());
        assertEquals(300, readSnapshot.totalDurationMs());
        assertEquals(200, readSnapshot.maxDurationMs());
        assertEquals(150.0, readSnapshot.averageDurationMs(), 0.001);

        ToolMetricsSnapshot writeSnapshot = snapshot.toolMetrics().get("writeFile");
        assertNotNull(writeSnapshot);
        assertEquals(1, writeSnapshot.accessDeniedCount());
        assertEquals("Tool ACL 阻断", writeSnapshot.lastFailureReason());
    }

    @Test
    @DisplayName("测试多线程高并发下的无锁计数准确性")
    void testConcurrentRecording() throws Exception {
        int threadCount = 20;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        collector.record("concurrentTool", 10, InvocationStatus.SUCCESS, null);
                        collector.record("concurrentTool", 20, InvocationStatus.ACCESS_DENIED, "阻断");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        McpMetricsSnapshot snapshot = collector.getSnapshot();
        long expectedTotal = (long) threadCount * operationsPerThread * 2;
        assertEquals(expectedTotal, snapshot.totalInvocations());
        assertEquals(expectedTotal / 2, snapshot.totalSuccess());
        assertEquals(expectedTotal / 2, snapshot.totalAccessDenied());
    }

    @Test
    @DisplayName("测试指标重置功能")
    void testReset() {
        collector.record("readFile", 100, InvocationStatus.SUCCESS, null);
        assertEquals(1, collector.getSnapshot().totalInvocations());

        collector.reset();
        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertEquals(0, snapshot.totalInvocations());
        assertEquals(0, snapshot.totalSuccess());
        assertTrue(snapshot.toolMetrics().isEmpty());
    }
}

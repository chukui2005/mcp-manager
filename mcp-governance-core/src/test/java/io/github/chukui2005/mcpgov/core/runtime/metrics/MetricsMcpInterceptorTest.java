package io.github.chukui2005.mcpgov.core.runtime.metrics;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 治理指标拦截器单元测试。
 */
class MetricsMcpInterceptorTest {

    private InMemoryMcpMetricsCollector collector;
    private MetricsMcpInterceptor interceptor;

    @BeforeEach
    void setUp() {
        collector = new InMemoryMcpMetricsCollector();
        interceptor = new MetricsMcpInterceptor(collector);
    }

    @Test
    @DisplayName("验证拦截器排序号为 -100，处于最外层")
    void testInterceptorOrder() {
        assertEquals(-100, interceptor.getOrder());
    }

    @Test
    @DisplayName("测试成功调用的生命周期度量收集")
    void testSuccessfulInvocation() {
        McpInvocationContext context = new McpInvocationContext("testTool", Map.of(), Map.of(), Instant.now());
        McpInvocationContext preContext = interceptor.preHandle(context);

        assertTrue(preContext.attributes().containsKey(MetricsMcpInterceptor.ATTR_START_TIME_MS));

        McpInvocationResult result = McpInvocationResult.success("OK", 15L);
        McpInvocationResult postResult = interceptor.postHandle(preContext, result);

        assertNotNull(postResult);
        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertEquals(1, snapshot.totalInvocations());
        assertEquals(1, snapshot.totalSuccess());
        assertEquals(0, snapshot.totalAccessDenied());

        ToolMetricsSnapshot toolSnapshot = snapshot.toolMetrics().get("testTool");
        assertNotNull(toolSnapshot);
        assertEquals(1, toolSnapshot.successCount());
    }

    @Test
    @DisplayName("测试 ACL 阻断异常被捕获为 ACCESS_DENIED 指标")
    void testAccessDeniedExceptionCaptured() {
        McpInvocationContext context = new McpInvocationContext("blockedTool", Map.of(), Map.of(), Instant.now());
        McpInvocationContext preContext = interceptor.preHandle(context);

        McpAccessDeniedException ex = new McpAccessDeniedException("Tool ACL 禁止调用该工具");
        interceptor.onError(preContext, ex);

        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertEquals(1, snapshot.totalInvocations());
        assertEquals(1, snapshot.totalAccessDenied());
        assertEquals(0, snapshot.totalSuccess());

        ToolMetricsSnapshot toolSnapshot = snapshot.toolMetrics().get("blockedTool");
        assertNotNull(toolSnapshot);
        assertEquals(1, toolSnapshot.accessDeniedCount());
        assertEquals("Tool ACL 禁止调用该工具", toolSnapshot.lastFailureReason());
    }

    @Test
    @DisplayName("测试参数安全校验异常被捕获为 VALIDATION_FAILED 指标")
    void testValidationExceptionCaptured() {
        McpInvocationContext context = new McpInvocationContext("validateTool", Map.of(), Map.of(), Instant.now());
        McpInvocationContext preContext = interceptor.preHandle(context);

        McpGovernanceException ex = new McpGovernanceException("参数缺少必填项");
        interceptor.onError(preContext, ex);

        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertEquals(1, snapshot.totalInvocations());
        assertEquals(1, snapshot.totalValidationFailed());

        ToolMetricsSnapshot toolSnapshot = snapshot.toolMetrics().get("validateTool");
        assertNotNull(toolSnapshot);
        assertEquals(1, toolSnapshot.validationFailedCount());
        assertEquals("参数缺少必填项", toolSnapshot.lastFailureReason());
    }

    @Test
    @DisplayName("测试业务执行错误被捕获为 ERROR 指标")
    void testGeneralErrorExceptionCaptured() {
        McpInvocationContext context = new McpInvocationContext("errorTool", Map.of(), Map.of(), Instant.now());
        McpInvocationContext preContext = interceptor.preHandle(context);

        RuntimeException ex = new RuntimeException("底层网络不可达");
        interceptor.onError(preContext, ex);

        McpMetricsSnapshot snapshot = collector.getSnapshot();
        assertEquals(1, snapshot.totalInvocations());
        assertEquals(1, snapshot.totalError());

        ToolMetricsSnapshot toolSnapshot = snapshot.toolMetrics().get("errorTool");
        assertNotNull(toolSnapshot);
        assertEquals(1, toolSnapshot.errorCount());
        assertEquals("底层网络不可达", toolSnapshot.lastFailureReason());
    }
}

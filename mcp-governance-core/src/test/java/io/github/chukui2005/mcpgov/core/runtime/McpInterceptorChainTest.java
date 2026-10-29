package io.github.chukui2005.mcpgov.core.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运行时拦截器核心模型与责任链单测。
 */
class McpInterceptorChainTest {

    @Test
    @DisplayName("验证 McpInvocationContext 的不可变特性与辅助方法")
    void testInvocationContextImmutability() {
        Map<String, Object> args = Map.of("key", "value");
        Map<String, Object> attrs = Map.of("userId", "u-123");
        Instant now = Instant.now();

        McpInvocationContext context = new McpInvocationContext("test-tool", args, attrs, now);

        assertThat(context.toolName()).isEqualTo("test-tool");
        assertThat(context.arguments()).containsEntry("key", "value");
        assertThat(context.attributes()).containsEntry("userId", "u-123");
        assertThat(context.timestamp()).isEqualTo(now);

        // 验证 Map 只读不可修改
        assertThatThrownBy(() -> context.arguments().put("newKey", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.attributes().put("newAttr", "val"))
                .isInstanceOf(UnsupportedOperationException.class);

        // 验证 withArguments 与 withAttribute
        McpInvocationContext modifiedArgs = context.withArguments(Map.of("key2", "val2"));
        assertThat(modifiedArgs.arguments()).containsEntry("key2", "val2");
        assertThat(modifiedArgs.toolName()).isEqualTo("test-tool");

        McpInvocationContext modifiedAttr = context.withAttribute("traceId", "t-999");
        assertThat(modifiedAttr.attributes()).containsEntry("userId", "u-123").containsEntry("traceId", "t-999");
    }

    @Test
    @DisplayName("验证 McpInvocationResult 的工厂方法")
    void testInvocationResultFactory() {
        McpInvocationResult success = McpInvocationResult.success("result-data", 42L);
        assertThat(success.success()).isTrue();
        assertThat(success.result()).isEqualTo("result-data");
        assertThat(success.errorMessage()).isNull();
        assertThat(success.durationMs()).isEqualTo(42L);

        McpInvocationResult failure = McpInvocationResult.failure("timeout", 100L);
        assertThat(failure.success()).isFalse();
        assertThat(failure.result()).isNull();
        assertThat(failure.errorMessage()).isEqualTo("timeout");
        assertThat(failure.durationMs()).isEqualTo(100L);
    }

    @Test
    @DisplayName("验证责任链的执行顺序：preHandle 升序、postHandle 逆序")
    void testInterceptorChainOrder() {
        List<String> executionOrder = new ArrayList<>();

        McpInterceptor first = new McpInterceptor() {
            @Override
            public int getOrder() {
                return 10;
            }

            @Override
            public McpInvocationContext preHandle(McpInvocationContext context) {
                executionOrder.add("pre-10");
                return context;
            }

            @Override
            public McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
                executionOrder.add("post-10");
                return result;
            }
        };

        McpInterceptor second = new McpInterceptor() {
            @Override
            public int getOrder() {
                return 20;
            }

            @Override
            public McpInvocationContext preHandle(McpInvocationContext context) {
                executionOrder.add("pre-20");
                return context;
            }

            @Override
            public McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
                executionOrder.add("post-20");
                return result;
            }
        };

        // 故意以无序集合传入
        McpInterceptorChain chain = new McpInterceptorChain(List.of(second, first));
        assertThat(chain.getInterceptors()).containsExactly(first, second);

        McpInvocationContext context = new McpInvocationContext("test-tool", Map.of(), Map.of(), Instant.now());
        McpInvocationResult result = McpInvocationResult.success("ok", 10L);

        chain.applyPreHandle(context);
        chain.applyPostHandle(context, result);

        // preHandle 升序：10 -> 20；postHandle 逆序：20 -> 10
        assertThat(executionOrder).containsExactly("pre-10", "pre-20", "post-20", "post-10");
    }

    @Test
    @DisplayName("验证前置拦截阻断（Fail-Fast）机制")
    void testFailFastOnAccessDenied() {
        List<String> auditTrail = new ArrayList<>();

        McpInterceptor securityInterceptor = new McpInterceptor() {
            @Override
            public int getOrder() {
                return 1;
            }

            @Override
            public McpInvocationContext preHandle(McpInvocationContext context) {
                auditTrail.add("security-check");
                throw new McpAccessDeniedException("禁止访问敏感工具: " + context.toolName());
            }
        };

        McpInterceptor followInterceptor = new McpInterceptor() {
            @Override
            public int getOrder() {
                return 2;
            }

            @Override
            public McpInvocationContext preHandle(McpInvocationContext context) {
                auditTrail.add("should-not-run");
                return context;
            }
        };

        McpInterceptorChain chain = new McpInterceptorChain(List.of(securityInterceptor, followInterceptor));
        McpInvocationContext context = new McpInvocationContext("forbidden_tool", Map.of(), Map.of(), Instant.now());

        assertThatThrownBy(() -> chain.applyPreHandle(context))
                .isInstanceOf(McpAccessDeniedException.class)
                .hasMessageContaining("禁止访问敏感工具: forbidden_tool");

        assertThat(auditTrail).containsExactly("security-check");
    }

    @Test
    @DisplayName("验证 onError 容错处理：单拦截器异常不中断其余拦截器")
    void testOnErrorFaultTolerance() {
        List<String> errorsCaught = new ArrayList<>();

        McpInterceptor buggyInterceptor = new McpInterceptor() {
            @Override
            public void onError(McpInvocationContext context, Throwable throwable) {
                throw new RuntimeException("buggy error handler");
            }
        };

        McpInterceptor normalInterceptor = new McpInterceptor() {
            @Override
            public void onError(McpInvocationContext context, Throwable throwable) {
                errorsCaught.add(throwable.getMessage());
            }
        };

        McpInterceptorChain chain = new McpInterceptorChain(List.of(buggyInterceptor, normalInterceptor));
        McpInvocationContext context = new McpInvocationContext("tool", Map.of(), Map.of(), Instant.now());

        chain.applyOnError(context, new RuntimeException("root error"));
        assertThat(errorsCaught).containsExactly("root error");
    }
}

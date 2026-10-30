package io.github.chukui2005.mcpgov.core.runtime.audit;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditMcpInterceptor 运行时审计生命周期拦截测试")
class AuditMcpInterceptorTest {

    @Test
    @DisplayName("验证拦截器排序号必须为 -50")
    void shouldHaveCorrectOrder() {
        AuditMcpInterceptor interceptor = new AuditMcpInterceptor(new DefaultMcpAuditPublisher());
        assertThat(interceptor.getOrder()).isEqualTo(-50);
    }

    @Test
    @DisplayName("成功调用场景：构建 SUCCESS 审计事件，脱敏入参并计算耗时")
    void shouldPublishSuccessEventOnPostHandle() throws InterruptedException {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        List<McpAuditEvent> events = new ArrayList<>();
        publisher.addListener(events::add);

        AuditMcpInterceptor interceptor = new AuditMcpInterceptor(publisher, true);

        McpInvocationContext context = new McpInvocationContext(
                "database_query",
                Map.of("sql", "SELECT * FROM users", "password", "superSecret123"),
                Map.of(AuditMcpInterceptor.ATTR_CALLER_ID, "agent-007"),
                Instant.now()
        );

        // preHandle 注入开始时间戳和 traceId
        McpInvocationContext prepared = interceptor.preHandle(context);
        assertThat(prepared.attributes()).containsKey(AuditMcpInterceptor.ATTR_AUDIT_START_TIME_MS);
        assertThat(prepared.attributes()).containsKey(AuditMcpInterceptor.ATTR_TRACE_ID);

        Thread.sleep(10); // 模拟耗时

        McpInvocationResult result = McpInvocationResult.success("Query completed", 10L);
        interceptor.postHandle(prepared, result);

        assertThat(events).hasSize(1);
        McpAuditEvent event = events.get(0);

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.toolName()).isEqualTo("database_query");
        assertThat(event.status()).isEqualTo(InvocationStatus.SUCCESS);
        assertThat(event.callerId()).isEqualTo("agent-007");
        assertThat(event.traceId()).isNotBlank();
        assertThat(event.durationMs()).isGreaterThanOrEqualTo(10L);
        assertThat(event.failureReason()).isNull();

        // 验证敏感词已被脱敏
        assertThat(event.arguments())
                .containsEntry("sql", "SELECT * FROM users")
                .containsEntry("password", "******");
    }

    @Test
    @DisplayName("ACL 阻断异常场景：构建 ACCESS_DENIED 审计事件")
    void shouldPublishAccessDeniedEventOnError() {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        List<McpAuditEvent> events = new ArrayList<>();
        publisher.addListener(events::add);

        AuditMcpInterceptor interceptor = new AuditMcpInterceptor(publisher);

        McpInvocationContext context = new McpInvocationContext(
                "bash_exec",
                Map.of("cmd", "rm -rf /", "token", "secretToken"),
                Map.of("clientIp", "10.0.0.1"),
                Instant.now()
        );
        McpInvocationContext prepared = interceptor.preHandle(context);

        McpAccessDeniedException ex = new McpAccessDeniedException("Tool [bash_exec] is blocked by Tool ACL policy");
        interceptor.onError(prepared, ex);

        assertThat(events).hasSize(1);
        McpAuditEvent event = events.get(0);

        assertThat(event.toolName()).isEqualTo("bash_exec");
        assertThat(event.status()).isEqualTo(InvocationStatus.ACCESS_DENIED);
        assertThat(event.failureReason()).contains("Tool [bash_exec] is blocked");
        assertThat(event.arguments().get("token")).isEqualTo("******");
    }

    @Test
    @DisplayName("参数校验失败场景：构建 VALIDATION_FAILED 审计事件")
    void shouldPublishValidationFailedEventOnError() {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        List<McpAuditEvent> events = new ArrayList<>();
        publisher.addListener(events::add);

        AuditMcpInterceptor interceptor = new AuditMcpInterceptor(publisher);

        McpInvocationContext context = new McpInvocationContext(
                "file_read",
                Map.of("path", "../../etc/shadow"),
                Map.of(),
                Instant.now()
        );
        McpInvocationContext prepared = interceptor.preHandle(context);

        McpGovernanceException ex = new McpGovernanceException("Parameter [path] violates traversal rules");
        interceptor.onError(prepared, ex);

        assertThat(events).hasSize(1);
        McpAuditEvent event = events.get(0);

        assertThat(event.toolName()).isEqualTo("file_read");
        assertThat(event.status()).isEqualTo(InvocationStatus.VALIDATION_FAILED);
        assertThat(event.failureReason()).contains("violates traversal rules");
    }

    @Test
    @DisplayName("未知内部异常场景：构建 ERROR 审计事件")
    void shouldPublishErrorEventOnError() {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        List<McpAuditEvent> events = new ArrayList<>();
        publisher.addListener(events::add);

        AuditMcpInterceptor interceptor = new AuditMcpInterceptor(publisher);

        McpInvocationContext context = new McpInvocationContext(
                "ai_predict",
                Map.of("prompt", "hello"),
                Map.of(),
                Instant.now()
        );
        McpInvocationContext prepared = interceptor.preHandle(context);

        RuntimeException ex = new RuntimeException("Model inference timeout");
        interceptor.onError(prepared, ex);

        assertThat(events).hasSize(1);
        McpAuditEvent event = events.get(0);

        assertThat(event.toolName()).isEqualTo("ai_predict");
        assertThat(event.status()).isEqualTo(InvocationStatus.ERROR);
        assertThat(event.failureReason()).isEqualTo("Model inference timeout");
    }
}

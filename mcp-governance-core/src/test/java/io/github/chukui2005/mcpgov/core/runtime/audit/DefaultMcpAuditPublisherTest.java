package io.github.chukui2005.mcpgov.core.runtime.audit;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("DefaultMcpAuditPublisher 事件广播与沙箱隔离测试")
class DefaultMcpAuditPublisherTest {

    @Test
    @DisplayName("成功向所有注册的监听器广播事件")
    void shouldPublishEventToAllListeners() {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        List<McpAuditEvent> received1 = new ArrayList<>();
        List<McpAuditEvent> received2 = new ArrayList<>();

        publisher.addListener(received1::add);
        publisher.addListener(received2::add);

        McpAuditEvent event = new McpAuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "trace-1",
                "caller-agent",
                "filesystem_read",
                Map.of("path", "/etc/hosts"),
                InvocationStatus.SUCCESS,
                15L,
                null,
                Map.of("clientIp", "127.0.0.1")
        );

        publisher.publish(event);

        assertThat(received1).hasSize(1).containsExactly(event);
        assertThat(received2).hasSize(1).containsExactly(event);
    }

    @Test
    @DisplayName("单个监听器抛出异常应进入沙箱隔离，不影响其他监听器通知")
    void shouldIsolateListenerExceptionAndContinueBroadcasting() {
        DefaultMcpAuditPublisher publisher = new DefaultMcpAuditPublisher();
        AtomicInteger successCounter = new AtomicInteger(0);

        // 第一个正常监听器
        publisher.addListener(e -> successCounter.incrementAndGet());

        // 第二个异常监听器
        publisher.addListener(e -> {
            throw new RuntimeException("Simulated listener exception");
        });

        // 第三个正常监听器
        publisher.addListener(e -> successCounter.incrementAndGet());

        McpAuditEvent event = new McpAuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "trace-2",
                null,
                "weather_query",
                Map.of(),
                InvocationStatus.SUCCESS,
                5L,
                null,
                Map.of()
        );

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        assertThat(successCounter.get()).isEqualTo(2);
    }
}

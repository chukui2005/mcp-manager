package io.github.chukui2005.mcpgovernance.gateway.audit;

import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryAuditRingBuffer 单元测试。
 */
class InMemoryAuditRingBufferTest {

    @Test
    @DisplayName("测试环形缓冲区容量上限截断与倒序读取")
    void testBufferCapacityTruncation() {
        InMemoryAuditRingBuffer ringBuffer = new InMemoryAuditRingBuffer(3);

        for (int i = 1; i <= 5; i++) {
            ringBuffer.publish(new McpAuditEvent(
                    "evt-" + i,
                    Instant.now(),
                    "trace-" + i,
                    "caller",
                    "tool-" + i,
                    Map.of("index", i),
                    InvocationStatus.SUCCESS,
                    10L,
                    null,
                    Map.of()
            ));
        }

        assertThat(ringBuffer.size()).isEqualTo(3);
        List<McpAuditEvent> events = ringBuffer.getRecentEvents(10);
        assertThat(events).hasSize(3);
        // 最新事件在前面: 5, 4, 3
        assertThat(events.get(0).eventId()).isEqualTo("evt-5");
        assertThat(events.get(1).eventId()).isEqualTo("evt-4");
        assertThat(events.get(2).eventId()).isEqualTo("evt-3");
    }

    @Test
    @DisplayName("测试事件监听器回调与异常隔离")
    void testListenerCallback() {
        InMemoryAuditRingBuffer ringBuffer = new InMemoryAuditRingBuffer(10);
        AtomicInteger count = new AtomicInteger();

        ringBuffer.addListener(evt -> count.incrementAndGet());
        // 抛出异常的监听器不应破坏其他流程
        ringBuffer.addListener(evt -> {
            throw new RuntimeException("模拟监听器异常");
        });

        ringBuffer.publish(new McpAuditEvent(
                "evt-1",
                Instant.now(),
                null,
                "caller",
                "calc",
                Map.of(),
                InvocationStatus.SUCCESS,
                5L,
                null,
                Map.of()
        ));

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("测试 Reactor SSE Flux 广播")
    void testFluxBroadcast() {
        InMemoryAuditRingBuffer ringBuffer = new InMemoryAuditRingBuffer(10);

        StepVerifier.create(ringBuffer.asFlux().take(1))
                .then(() -> {
                    ringBuffer.publish(new McpAuditEvent(
                            "evt-flux",
                            Instant.now(),
                            null,
                            "caller",
                            "read_file",
                            Map.of(),
                            InvocationStatus.SUCCESS,
                            8L,
                            null,
                            Map.of()
                    ));
                })
                .assertNext(event -> {
                    assertThat(event.eventId()).isEqualTo("evt-flux");
                    assertThat(event.toolName()).isEqualTo("read_file");
                })
                .verifyComplete();
    }
}

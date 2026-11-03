package io.github.chukui2005.mcpgovernance.gateway.audit;

import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditListener;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存审计事件环形缓冲器与 SSE 广播机制。
 * 遵循《AGENTS.md》与 ADR 0011：
 * 1. 作为 {@link McpAuditPublisher} 实现并支持作为 Spring Bean 注册；
 * 2. 线程安全维护指定容量（默认 200 条）的最近脱敏审计事件记录；
 * 3. 基于 Reactor Sinks 实现实时多播广播，为 SSE 端点提供无压力的事件订阅流；
 * 4. 严禁暴露未脱敏数据，保证内存安全与高效滚动。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class InMemoryAuditRingBuffer implements McpAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditRingBuffer.class);

    /**
     * 默认环形队列最大容量。
     */
    public static final int DEFAULT_MAX_CAPACITY = 200;

    private final int maxCapacity;
    private final Deque<McpAuditEvent> buffer = new ConcurrentLinkedDeque<>();
    private final List<McpAuditListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Reactor 多播 Sink，用于向活跃的 Web SSE 订阅者广播审计事件。
     */
    private final Sinks.Many<McpAuditEvent> sink = Sinks.many().multicast().directBestEffort();

    /**
     * 默认构造函数（使用默认容量 200）。
     */
    public InMemoryAuditRingBuffer() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * 指定最大容量构造函数。
     *
     * @param maxCapacity 环形队列最大容量
     */
    public InMemoryAuditRingBuffer(int maxCapacity) {
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : DEFAULT_MAX_CAPACITY;
    }

    @Override
    public void publish(McpAuditEvent event) {
        if (event == null) {
            return;
        }

        // 存入环形队列头部（最新事件在前）
        buffer.addFirst(event);

        // 淘汰超期旧事件
        while (buffer.size() > maxCapacity) {
            buffer.pollLast();
        }

        // 触发注册的监听器
        for (McpAuditListener listener : listeners) {
            try {
                listener.onAuditEvent(event);
            } catch (Throwable t) {
                log.warn("审计监听器执行异常: listener={}, error={}", listener.getClass().getName(), t.getMessage());
            }
        }

        // 向 SSE 订阅者广播
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.debug("SSE 广播事件降级/丢弃: eventId={}, result={}", event.eventId(), result);
        }
    }

    @Override
    public void addListener(McpAuditListener listener) {
        Objects.requireNonNull(listener, "listener 不能为 null");
        listeners.add(listener);
    }

    /**
     * 获取最近缓存的审计事件快照列表（按时间倒序排列，最新事件排在前面）。
     *
     * @param limit 最大返回条数
     * @return 不可变审计事件列表
     */
    public List<McpAuditEvent> getRecentEvents(int limit) {
        int safeLimit = limit > 0 ? limit : 50;
        List<McpAuditEvent> snapshot = new ArrayList<>();
        int count = 0;
        for (McpAuditEvent event : buffer) {
            snapshot.add(event);
            count++;
            if (count >= safeLimit) {
                break;
            }
        }
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * 订阅实时审计事件流（Flux 响应式流）。
     *
     * @return 审计事件 Flux
     */
    public Flux<McpAuditEvent> asFlux() {
        return sink.asFlux();
    }

    /**
     * 获取当前环形缓冲区中的事件总数。
     *
     * @return 当前事件数量
     */
    public int size() {
        return buffer.size();
    }

    /**
     * 清空当前环形缓冲区。
     */
    public void clear() {
        buffer.clear();
    }
}

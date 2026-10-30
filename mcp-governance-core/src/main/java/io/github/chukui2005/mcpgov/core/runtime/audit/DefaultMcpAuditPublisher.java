package io.github.chukui2005.mcpgov.core.runtime.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 纯 Java 默认审计事件发布器。
 * 内部维护并发安全的监听器列表（{@link CopyOnWriteArrayList}），
 * 并提供异常隔离沙箱保护机制，防止个别监听器异常阻断主流程。
 */
public class DefaultMcpAuditPublisher implements McpAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpAuditPublisher.class);

    private final List<McpAuditListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 默认构造函数。
     */
    public DefaultMcpAuditPublisher() {
    }

    @Override
    public void publish(McpAuditEvent event) {
        Objects.requireNonNull(event, "event 不能为 null");
        for (McpAuditListener listener : listeners) {
            try {
                listener.onAuditEvent(event);
            } catch (Throwable t) {
                log.warn("MCP 审计监听器 [{}] 执行异常，已安全隔离：{}", listener.getClass().getName(), t.getMessage(), t);
            }
        }
    }

    @Override
    public void addListener(McpAuditListener listener) {
        Objects.requireNonNull(listener, "listener 不能为 null");
        listeners.add(listener);
    }

    /**
     * 获取当前所有已注册的监听器只读列表。
     *
     * @return 监听器列表
     */
    public List<McpAuditListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}

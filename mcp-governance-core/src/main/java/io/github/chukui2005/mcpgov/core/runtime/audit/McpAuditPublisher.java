package io.github.chukui2005.mcpgov.core.runtime.audit;

/**
 * 运行时 MCP 审计事件发布器契约。
 */
public interface McpAuditPublisher {

    /**
     * 发布一个结构化审计事件。
     *
     * @param event 审计事件，非空
     */
    void publish(McpAuditEvent event);

    /**
     * 注册审计监听器。
     *
     * @param listener 监听器，非空
     */
    void addListener(McpAuditListener listener);
}

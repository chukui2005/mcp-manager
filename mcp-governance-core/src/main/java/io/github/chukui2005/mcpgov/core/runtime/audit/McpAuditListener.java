package io.github.chukui2005.mcpgov.core.runtime.audit;

/**
 * 运行时 MCP 审计事件监听器函数式接口。
 */
@FunctionalInterface
public interface McpAuditListener {

    /**
     * 处理结构化运行时审计事件。
     *
     * @param event 不可变审计事件记录，非空
     */
    void onAuditEvent(McpAuditEvent event);
}

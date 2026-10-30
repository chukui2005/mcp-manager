package io.github.chukui2005.mcpgov.spring.boot.audit;

import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring 环境下的 MCP 运行时结构化审计事件包装类。
 * 继承自 Spring {@link ApplicationEvent}，使宿主应用能够通过原生
 * {@code @EventListener} 或 {@code @Async} 轻松捕获并异步处理审计事件。
 */
public class SpringMcpAuditEvent extends ApplicationEvent {

    private final McpAuditEvent auditEvent;

    /**
     * 构造 Spring 审计事件。
     *
     * @param source 事件源对象，非空
     * @param auditEvent 核心不可变审计事件记录，非空
     */
    public SpringMcpAuditEvent(Object source, McpAuditEvent auditEvent) {
        super(source);
        this.auditEvent = Objects.requireNonNull(auditEvent, "auditEvent 不能为 null");
    }

    /**
     * 获取核心不可变审计事件。
     *
     * @return 审计事件记录
     */
    public McpAuditEvent getAuditEvent() {
        return auditEvent;
    }
}

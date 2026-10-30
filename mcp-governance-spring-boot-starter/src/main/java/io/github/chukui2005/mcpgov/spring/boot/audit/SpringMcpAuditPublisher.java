package io.github.chukui2005.mcpgov.spring.boot.audit;

import io.github.chukui2005.mcpgov.core.runtime.audit.DefaultMcpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.util.Objects;

/**
 * 桥接 Spring 容器事件总线的审计事件发布器。
 * 继承自 {@link DefaultMcpAuditPublisher}，既保留分发给已注册的纯 Java {@link io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditListener} 能力，
 * 又将事件包装为 {@link SpringMcpAuditEvent} 广播到 Spring 上下文中。
 */
public class SpringMcpAuditPublisher extends DefaultMcpAuditPublisher implements ApplicationEventPublisherAware {

    private ApplicationEventPublisher applicationEventPublisher;
    private final boolean springEventEnabled;

    /**
     * 默认构造函数，默认开启 Spring 事件广播。
     */
    public SpringMcpAuditPublisher() {
        this(true);
    }

    /**
     * 构造函数。
     *
     * @param springEventEnabled 是否开启 Spring 事件广播
     */
    public SpringMcpAuditPublisher(boolean springEventEnabled) {
        this.springEventEnabled = springEventEnabled;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(McpAuditEvent event) {
        Objects.requireNonNull(event, "event 不能为 null");
        // 1. 先触发所有纯 Java 监听器（具备异常沙箱隔离保护）
        super.publish(event);

        // 2. 若开启 Spring 事件广播且已注入 ApplicationEventPublisher，发布 Spring 事件
        if (springEventEnabled && applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new SpringMcpAuditEvent(this, event));
        }
    }

    /**
     * 是否开启了 Spring 事件总线广播。
     *
     * @return 是否开启
     */
    public boolean isSpringEventEnabled() {
        return springEventEnabled;
    }
}

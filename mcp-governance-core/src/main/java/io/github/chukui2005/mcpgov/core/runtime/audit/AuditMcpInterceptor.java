package io.github.chukui2005.mcpgov.core.runtime.audit;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * MCP 运行时审计核心拦截器。
 * 位于责任链外层位置（Order: -50），负责在整个调用生命周期中捕获成功、ACL 阻断、参数校验失败与未知异常，
 * 并对参数执行脱敏后构建不可变 {@link McpAuditEvent}，发布到 {@link McpAuditPublisher}。
 */
public class AuditMcpInterceptor implements McpInterceptor {

    /**
     * 上下文属性 Key：调用开始时间戳（毫秒）。
     */
    public static final String ATTR_AUDIT_START_TIME_MS = "_mcp_audit_start_time_ms";

    /**
     * 上下文属性 Key：分布式链路追踪 ID。
     */
    public static final String ATTR_TRACE_ID = "traceId";

    /**
     * 上下文属性 Key：调用方或 Agent 身份标识。
     */
    public static final String ATTR_CALLER_ID = "callerId";

    private final McpAuditPublisher publisher;
    private final boolean maskSensitiveArguments;

    /**
     * 构造审计拦截器，默认启用敏感字段脱敏。
     *
     * @param publisher 审计事件发布器，非空
     */
    public AuditMcpInterceptor(McpAuditPublisher publisher) {
        this(publisher, true);
    }

    /**
     * 构造审计拦截器。
     *
     * @param publisher 审计事件发布器，非空
     * @param maskSensitiveArguments 是否对敏感入参打码脱敏
     */
    public AuditMcpInterceptor(McpAuditPublisher publisher, boolean maskSensitiveArguments) {
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为 null");
        this.maskSensitiveArguments = maskSensitiveArguments;
    }

    @Override
    public int getOrder() {
        return -50;
    }

    @Override
    public McpInvocationContext preHandle(McpInvocationContext context) {
        McpInvocationContext current = context.withAttribute(ATTR_AUDIT_START_TIME_MS, System.currentTimeMillis());
        // 若上下文中未包含 traceId，则自动生成 UUID 补全链路追踪
        if (!hasAttribute(current, ATTR_TRACE_ID)) {
            current = current.withAttribute(ATTR_TRACE_ID, UUID.randomUUID().toString());
        }
        return current;
    }

    @Override
    public McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
        long duration = calculateDuration(context, result != null ? result.durationMs() : 0L);
        Map<String, Object> arguments = prepareArguments(context);
        String traceId = extractStringAttribute(context, ATTR_TRACE_ID);
        String callerId = extractStringAttribute(context, ATTR_CALLER_ID);

        McpAuditEvent event = new McpAuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                traceId,
                callerId,
                context.toolName(),
                arguments,
                InvocationStatus.SUCCESS,
                duration,
                null,
                context.attributes()
        );
        publisher.publish(event);
        return result;
    }

    @Override
    public void onError(McpInvocationContext context, Throwable throwable) {
        long duration = calculateDuration(context, 0L);
        InvocationStatus status;
        if (throwable instanceof McpAccessDeniedException) {
            status = InvocationStatus.ACCESS_DENIED;
        } else if (throwable instanceof McpGovernanceException) {
            status = InvocationStatus.VALIDATION_FAILED;
        } else {
            status = InvocationStatus.ERROR;
        }

        String failureReason = throwable != null ? throwable.getMessage() : "未知异常";
        Map<String, Object> arguments = prepareArguments(context);
        String traceId = extractStringAttribute(context, ATTR_TRACE_ID);
        String callerId = extractStringAttribute(context, ATTR_CALLER_ID);

        McpAuditEvent event = new McpAuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                traceId,
                callerId,
                context != null ? context.toolName() : "UNKNOWN_TOOL",
                arguments,
                status,
                duration,
                failureReason,
                context != null ? context.attributes() : Map.of()
        );
        publisher.publish(event);
    }

    /**
     * 获取持有的发布器。
     *
     * @return 审计事件发布器
     */
    public McpAuditPublisher getPublisher() {
        return publisher;
    }

    /**
     * 是否对敏感入参进行脱敏。
     *
     * @return 是否脱敏
     */
    public boolean isMaskSensitiveArguments() {
        return maskSensitiveArguments;
    }

    private Map<String, Object> prepareArguments(McpInvocationContext context) {
        if (context == null || context.arguments() == null) {
            return Map.of();
        }
        if (maskSensitiveArguments) {
            return SensitiveDataMasker.mask(context.arguments());
        }
        return context.arguments();
    }

    private boolean hasAttribute(McpInvocationContext context, String key) {
        return context != null && context.attributes() != null && context.attributes().containsKey(key)
                && context.attributes().get(key) != null;
    }

    private String extractStringAttribute(McpInvocationContext context, String key) {
        if (context != null && context.attributes() != null) {
            Object val = context.attributes().get(key);
            if (val != null) {
                return String.valueOf(val);
            }
        }
        return null;
    }

    private long calculateDuration(McpInvocationContext context, long fallbackDuration) {
        if (context != null && context.attributes() != null) {
            Object startTimeObj = context.attributes().get(ATTR_AUDIT_START_TIME_MS);
            if (startTimeObj instanceof Number num) {
                return Math.max(0L, System.currentTimeMillis() - num.longValue());
            }
        }
        return Math.max(0L, fallbackDuration);
    }
}

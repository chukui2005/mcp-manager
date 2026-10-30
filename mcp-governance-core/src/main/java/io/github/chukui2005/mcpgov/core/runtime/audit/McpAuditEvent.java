package io.github.chukui2005.mcpgov.core.runtime.audit;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变结构化运行时 MCP 治理审计记录（Record）。
 * 满足 OWASP MCP01 / MCP08 合规要求，记录工具调用生命周期的完整快照与治理结果。
 *
 * @param eventId 全局唯一审计事件 ID，非空
 * @param timestamp 审计事件生成时间戳，非空
 * @param traceId 分布式链路追踪 ID，允许为 null
 * @param callerId 调用方或 Agent 主体唯一标识，允许为 null
 * @param toolName 被调用的 MCP 工具名称，非空
 * @param arguments 已脱敏处理的入参键值对映射，不可变防篡改
 * @param status 调用治理最终状态，非空
 * @param durationMs 工具调用与治理链路总执行耗时（毫秒）
 * @param failureReason 阻断违规或底层调用失败的原因说明，成功调用为 null
 * @param attributes 治理上下文只读元数据与环境快照，不可变只读
 */
public record McpAuditEvent(
        String eventId,
        Instant timestamp,
        String traceId,
        String callerId,
        String toolName,
        Map<String, Object> arguments,
        InvocationStatus status,
        long durationMs,
        String failureReason,
        Map<String, Object> attributes
) {
    /**
     * 规范构造函数，强制执行非空断言并返回只读防篡改视图。
     */
    public McpAuditEvent {
        Objects.requireNonNull(eventId, "eventId 不能为 null");
        Objects.requireNonNull(timestamp, "timestamp 不能为 null");
        Objects.requireNonNull(toolName, "toolName 不能为 null");
        Objects.requireNonNull(status, "status 不能为 null");
        arguments = arguments != null ? Collections.unmodifiableMap(new HashMap<>(arguments)) : Map.of();
        attributes = attributes != null ? Collections.unmodifiableMap(new HashMap<>(attributes)) : Map.of();
    }
}

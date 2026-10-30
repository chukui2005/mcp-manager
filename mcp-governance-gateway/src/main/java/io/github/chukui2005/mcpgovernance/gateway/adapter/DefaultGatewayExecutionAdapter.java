package io.github.chukui2005.mcpgovernance.gateway.adapter;

import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认网关执行适配器实现。
 * 负责构建 Core 的 McpInvocationContext，并在当前无外部 UpstreamClient 连接池时提供安全的回显/模拟执行。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class DefaultGatewayExecutionAdapter implements GatewayExecutionAdapter {

    private static final Logger log = LoggerFactory.getLogger(DefaultGatewayExecutionAdapter.class);

    /**
     * 默认构造函数。
     */
    public DefaultGatewayExecutionAdapter() {
    }

    @Override
    public McpInvocationContext buildContext(RouteMatchResult matchResult, Map<String, Object> arguments, String clientName) {
        String serverId = matchResult.getRouteDefinition().getId();
        String targetToolName = matchResult.getTargetToolName();
        Map<String, Object> safeArgs = arguments != null ? new HashMap<>(arguments) : Collections.emptyMap();
        String safeClient = clientName != null && !clientName.trim().isEmpty() ? clientName : "mcp-gateway-client";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("serverId", serverId);
        attributes.put("callerApplication", safeClient);
        attributes.put("matchedPrefix", matchResult.getMatchedPrefix());

        return new McpInvocationContext(
                targetToolName,
                safeArgs,
                attributes,
                Instant.now()
        );
    }

    @Override
    public McpInvocationResult execute(McpInvocationContext context) {
        long start = System.currentTimeMillis();
        String serverId = (String) context.attributes().getOrDefault("serverId", "unknown");
        String caller = (String) context.attributes().getOrDefault("callerApplication", "anonymous");

        log.info("网关派发受治理工具调用: tool={}, serverId={}, caller={}",
                context.toolName(), serverId, caller);

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("tool", context.toolName());
        mockPayload.put("serverId", serverId);
        mockPayload.put("status", "SUCCESS");
        mockPayload.put("arguments", context.arguments());

        long durationMs = System.currentTimeMillis() - start;
        return McpInvocationResult.success(mockPayload, durationMs);
    }
}

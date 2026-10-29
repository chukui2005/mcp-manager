package io.github.chukui2005.mcpgov.spring.boot.interceptor;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.spring.boot.autoconfigure.McpGovernanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 开箱即用的 MCP 调用审计与敏感参数脱敏拦截器。
 * 默认排序为 1000（最外层），负责打印结构化调用审计日志，并对敏感字段进行安全掩码。
 */
public class LoggingMcpInterceptor implements McpInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingMcpInterceptor.class);

    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "token", "apikey", "api_key", "authorization", "privatekey", "private_key"
    );

    private final McpGovernanceProperties properties;

    /**
     * 构造审计日志拦截器。
     *
     * @param properties 治理配置属性
     */
    public LoggingMcpInterceptor(McpGovernanceProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public McpInvocationContext preHandle(McpInvocationContext context) {
        Map<String, Object> processedArgs = context.arguments();
        if (properties.getAudit().isMaskSensitiveArguments() && !processedArgs.isEmpty()) {
            processedArgs = maskArguments(processedArgs);
        }

        if (properties.getAudit().isEnabled()) {
            logAudit("MCP Invocation PreHandle - Tool: [{}], Attributes: {}, MaskedArgs: {}",
                    context.toolName(), context.attributes(), processedArgs);
        }

        // 返回脱敏后的上下文副本，防止下游拦截器或日志暴露明文
        return context.withArguments(processedArgs);
    }

    @Override
    public McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
        if (properties.getAudit().isEnabled()) {
            logAudit("MCP Invocation PostHandle - Tool: [{}], Success: [{}], Duration: [{}ms], Error: [{}]",
                    context.toolName(), result.success(), result.durationMs(), result.errorMessage());
        }
        return result;
    }

    @Override
    public void onError(McpInvocationContext context, Throwable throwable) {
        if (properties.getAudit().isEnabled()) {
            log.warn("MCP Invocation Error - Tool: [{}], Exception: [{}]", context.toolName(), throwable.getMessage());
        }
    }

    private Map<String, Object> maskArguments(Map<String, Object> originalArgs) {
        Map<String, Object> masked = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalArgs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                masked.put(key, "******");
            } else if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) nestedMap;
                masked.put(key, maskArguments(casted));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private void logAudit(String format, Object... arguments) {
        String level = properties.getAudit().getLogLevel();
        if ("DEBUG".equalsIgnoreCase(level)) {
            log.debug(format, arguments);
        } else if ("WARN".equalsIgnoreCase(level)) {
            log.warn(format, arguments);
        } else {
            log.info(format, arguments);
        }
    }
}

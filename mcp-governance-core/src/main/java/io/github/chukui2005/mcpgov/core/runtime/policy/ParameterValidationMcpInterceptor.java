package io.github.chukui2005.mcpgov.core.runtime.policy;

import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 工具调用入参校验运行时拦截器。
 * 优先级顺序号默认为 200，在 ACL 判定之后执行，负责必填项与正/反向正则校验。
 * 治理入参注入与路径遍历等安全风险。
 */
public class ParameterValidationMcpInterceptor implements McpInterceptor {

    private final List<ParameterValidationRule> rules;
    private static final Map<String, Pattern> COMPILED_PATTERNS = new ConcurrentHashMap<>();

    /**
     * 构造函数。
     *
     * @param rules 参数校验规则列表
     */
    public ParameterValidationMcpInterceptor(List<ParameterValidationRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * 获取配置的参数校验规则列表。
     *
     * @return 不可变规则列表
     */
    public List<ParameterValidationRule> getRules() {
        return rules;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public McpInvocationContext preHandle(McpInvocationContext context) {
        Objects.requireNonNull(context, "context 不能为 null");
        if (rules.isEmpty()) {
            return context;
        }

        String toolName = context.toolName();
        Map<String, Object> arguments = context.arguments();

        for (ParameterValidationRule rule : rules) {
            if (!rule.appliesTo(toolName)) {
                continue;
            }

            String paramName = rule.parameterName();
            Object rawValue = arguments.get(paramName);

            // 1. 必填性校验
            if (rule.required() && (rawValue == null || String.valueOf(rawValue).isBlank())) {
                String msg = rule.errorMessage() != null && !rule.errorMessage().isBlank()
                    ? rule.errorMessage()
                    : String.format("Parameter '%s' is required for tool '%s'", paramName, toolName);
                throw new McpGovernanceException(msg);
            }

            if (rawValue != null) {
                String strValue = String.valueOf(rawValue);

                // 2. 禁用模式校验（命中则阻断）
                if (rule.disallowedRegex() != null && !rule.disallowedRegex().isBlank()) {
                    Pattern disallowed = COMPILED_PATTERNS.computeIfAbsent(rule.disallowedRegex(), Pattern::compile);
                    if (disallowed.matcher(strValue).matches()) {
                        String msg = rule.errorMessage() != null && !rule.errorMessage().isBlank()
                            ? rule.errorMessage()
                            : String.format("Parameter '%s' violates disallowed pattern for tool '%s'", paramName, toolName);
                        throw new McpGovernanceException(msg);
                    }
                }

                // 3. 必需满足模式校验（未命中则阻断）
                if (rule.requiredRegex() != null && !rule.requiredRegex().isBlank()) {
                    Pattern required = COMPILED_PATTERNS.computeIfAbsent(rule.requiredRegex(), Pattern::compile);
                    if (!required.matcher(strValue).matches()) {
                        String msg = rule.errorMessage() != null && !rule.errorMessage().isBlank()
                            ? rule.errorMessage()
                            : String.format("Parameter '%s' does not match required pattern for tool '%s'", paramName, toolName);
                        throw new McpGovernanceException(msg);
                    }
                }
            }
        }

        return context;
    }
}

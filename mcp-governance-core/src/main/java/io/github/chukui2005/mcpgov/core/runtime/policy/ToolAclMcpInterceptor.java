package io.github.chukui2005.mcpgov.core.runtime.policy;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;

import java.util.Objects;

/**
 * Tool ACL 访问控制运行时拦截器。
 * 优先级顺序号默认为 100，负责在前置阶段对目标工具进行黑名单与白名单权限校验。
 * 治理 OWASP MCP02 权限蔓延风险。
 */
public class ToolAclMcpInterceptor implements McpInterceptor {

    private final ToolAclPolicy policy;

    /**
     * 构造函数。
     *
     * @param policy 访问控制策略
     */
    public ToolAclMcpInterceptor(ToolAclPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy 不能为 null");
    }

    /**
     * 获取访问控制策略定义。
     *
     * @return 策略对象
     */
    public ToolAclPolicy getPolicy() {
        return policy;
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public McpInvocationContext preHandle(McpInvocationContext context) {
        Objects.requireNonNull(context, "context 不能为 null");
        String toolName = context.toolName();
        if (!policy.isAllowed(toolName)) {
            throw new McpAccessDeniedException(
                String.format("Tool '%s' is rejected by ACL policy [%s] (OWASP MCP02 Scope Creep Violation)",
                    toolName, policy.defaultMode())
            );
        }
        return context;
    }
}

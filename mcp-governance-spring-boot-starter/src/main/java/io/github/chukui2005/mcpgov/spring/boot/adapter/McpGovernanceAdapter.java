package io.github.chukui2005.mcpgov.spring.boot.adapter;

import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 治理端到端统一适配器。
 * 为宿主业务系统或网关层提供一行接入运行时治理拦截周期的便捷模板。
 */
public class McpGovernanceAdapter {

    private final McpInterceptorChain chain;

    /**
     * 回调接口：执行底层真实的 Tool 业务逻辑。
     */
    @FunctionalInterface
    public interface ToolExecutionCallback {
        /**
         * 执行工具逻辑。
         *
         * @param arguments 经过拦截器脱敏或校验后的参数
         * @return 工具执行产物
         * @throws Throwable 执行中可能抛出的任意异常
         */
        Object execute(Map<String, Object> arguments) throws Throwable;
    }

    /**
     * 构造函数。
     *
     * @param chain 治理拦截责任链
     */
    public McpGovernanceAdapter(McpInterceptorChain chain) {
        this.chain = Objects.requireNonNull(chain, "chain 不能为 null");
    }

    /**
     * 在治理拦截责任链保护下执行工具调用。
     *
     * @param toolName 工具名称
     * @param arguments 调用入参字典
     * @param attributes 调用者上下文属性
     * @param callback 实际业务工具执行回调
     * @return 治理包装后的调用结果
     */
    public McpInvocationResult executeGoverned(
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> attributes,
        ToolExecutionCallback callback
    ) {
        Objects.requireNonNull(callback, "callback 不能为 null");
        McpInvocationContext initialContext = new McpInvocationContext(toolName, arguments, attributes, Instant.now());
        McpInvocationContext governedContext = chain.applyPreHandle(initialContext);
        long start = System.currentTimeMillis();
        try {
            Object rawResult = callback.execute(governedContext.arguments());
            long duration = System.currentTimeMillis() - start;
            return chain.applyPostHandle(governedContext, McpInvocationResult.success(rawResult, duration));
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            chain.applyOnError(governedContext, t);
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new McpGovernanceException("Tool execution failed: " + t.getMessage(), t);
        }
    }
}

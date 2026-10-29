package io.github.chukui2005.mcpgov.core.runtime;

/**
 * MCP 运行时拦截器核心接口（纯 Java 契约）。
 * 拦截器可按排序号构成责任链，在前置、后置以及异常处理阶段介入请求生命周期。
 */
public interface McpInterceptor {

    /**
     * 获取拦截器优先级排序号，数值越小越优先执行。
     *
     * @return 排序号，默认 0
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 前置拦截处理。在调用实际工具前执行。
     * 可用于参数脱敏、访问控制判定、上下文增强等。
     *
     * @param context 当前调用上下文
     * @return 经处理后的调用上下文（可为原对象或脱敏后的新对象）
     * @throws McpGovernanceException 当发生策略阻断或鉴权失败时抛出
     */
    default McpInvocationContext preHandle(McpInvocationContext context) {
        return context;
    }

    /**
     * 后置拦截处理。在工具执行成功后按逆序执行。
     * 可用于出参脱敏、调用耗时记录、审计日志记录等。
     *
     * @param context 当前调用上下文
     * @param result 当前执行结果
     * @return 经处理后的执行结果
     */
    default McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
        return result;
    }

    /**
     * 异常拦截处理。在工具执行或前置拦截发生不可恢复异常时回调。
     *
     * @param context 当前调用上下文
     * @param throwable 发生的异常对象
     */
    default void onError(McpInvocationContext context, Throwable throwable) {
    }
}

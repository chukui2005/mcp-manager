package io.github.chukui2005.mcpgov.core.runtime;

/**
 * 运行时工具调用执行结果模型（不可变）。
 *
 * @param success 是否执行成功
 * @param result 工具执行成功返回值，失败时通常为 null
 * @param errorMessage 执行失败时的简报说明，成功时通常为 null
 * @param durationMs 执行耗时毫秒数
 */
public record McpInvocationResult(
        boolean success,
        Object result,
        String errorMessage,
        long durationMs
) {
    /**
     * 构建调用成功的不可变结果对象。
     *
     * @param result 返回值
     * @param durationMs 耗时毫秒数
     * @return 成功结果
     */
    public static McpInvocationResult success(Object result, long durationMs) {
        return new McpInvocationResult(true, result, null, durationMs);
    }

    /**
     * 构建调用失败的不可变结果对象。
     *
     * @param errorMessage 错误简报
     * @param durationMs 耗时毫秒数
     * @return 失败结果
     */
    public static McpInvocationResult failure(String errorMessage, long durationMs) {
        return new McpInvocationResult(false, null, errorMessage, durationMs);
    }
}

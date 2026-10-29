package io.github.chukui2005.mcpgov.core.runtime;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * MCP 运行时拦截器链执行器（纯 Java 责任链实现）。
 * 负责调度有序拦截器列表的前置校验、后置审计以及异常处理。
 */
public class McpInterceptorChain {

    private final List<McpInterceptor> interceptors;

    /**
     * 构造拦截器链执行器，并对传入的拦截器集合按照 getOrder() 进行升序排序。
     *
     * @param interceptors 拦截器列表，允许为 null 或空集合
     */
    public McpInterceptorChain(List<McpInterceptor> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            this.interceptors = Collections.emptyList();
        } else {
            this.interceptors = interceptors.stream()
                    .sorted(Comparator.comparingInt(McpInterceptor::getOrder))
                    .toList();
        }
    }

    /**
     * 依次升序执行所有拦截器的 preHandle 方法。
     * 若任一拦截器抛出异常，执行将立即阻断（Fail-Fast）。
     *
     * @param context 初始调用上下文
     * @return 经所有前置拦截器处理后的最终上下文
     */
    public McpInvocationContext applyPreHandle(McpInvocationContext context) {
        McpInvocationContext current = context;
        for (McpInterceptor interceptor : interceptors) {
            current = interceptor.preHandle(current);
        }
        return current;
    }

    /**
     * 依次逆序执行所有拦截器的 postHandle 方法。
     *
     * @param context 当前调用上下文
     * @param result 初始执行结果
     * @return 经所有后置拦截器处理后的最终结果
     */
    public McpInvocationResult applyPostHandle(McpInvocationContext context, McpInvocationResult result) {
        McpInvocationResult current = result;
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            current = interceptors.get(i).postHandle(context, current);
        }
        return current;
    }

    /**
     * 依次执行所有拦截器的 onError 方法，自动屏蔽单个拦截器的处理异常，确保遥测通知尽可能全覆盖。
     *
     * @param context 当前调用上下文
     * @param ex 异常对象
     */
    public void applyOnError(McpInvocationContext context, Throwable ex) {
        for (McpInterceptor interceptor : interceptors) {
            try {
                interceptor.onError(context, ex);
            } catch (Exception ignored) {
                // 忽略异常回调中的附带异常，避免掩盖核心错误
            }
        }
    }

    /**
     * 获取当前拦截器链中的只读拦截器列表。
     *
     * @return 不可变的拦截器列表
     */
    public List<McpInterceptor> getInterceptors() {
        return interceptors;
    }
}

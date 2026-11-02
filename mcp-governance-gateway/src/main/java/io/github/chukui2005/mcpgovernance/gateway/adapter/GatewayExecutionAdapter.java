package io.github.chukui2005.mcpgovernance.gateway.adapter;

import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteMatchResult;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关与 Core 治理层调用的适配接口。
 * 将入站 JSON-RPC 工具调用适配构建为 Core 治理内核的 McpInvocationContext 并驱动治理责任链。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public interface GatewayExecutionAdapter {

    /**
     * 将解析后的工具调用上下文构建为 McpInvocationContext。
     *
     * @param matchResult 路由匹配结果
     * @param arguments   请求入参 Map
     * @param clientName  客户端标识
     * @return Core 治理调用上下文对象
     */
    McpInvocationContext buildContext(RouteMatchResult matchResult, Map<String, Object> arguments, String clientName);

    /**
     * 同步执行受治理的工具调用（兼容历史与同步调用场景）。
     *
     * @param context Core 调用上下文
     * @return 调用结果对象
     */
    McpInvocationResult execute(McpInvocationContext context);

    /**
     * 响应式执行受治理的工具调用（非阻塞，支持响应式 WebFlux 线程安全调用）。
     *
     * @param context Core 调用上下文
     * @return 响应式包含的工具调用执行结果
     */
    default Mono<McpInvocationResult> executeReactive(McpInvocationContext context) {
        return Mono.fromCallable(() -> execute(context));
    }
}

package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 下游 MCP Server 出站客户端 SPI 接口。
 * 屏蔽不同底层协议（HTTP、SSE、STDIO 等）之间的通信差异，
 * 提供统一的工具发现、工具远程执行、健康探测与生命周期关闭能力。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public interface UpstreamClient extends AutoCloseable {

    /**
     * 获取出站客户端所属的下游路由 ID。
     *
     * @return 路由唯一标识符
     */
    String getRouteId();

    /**
     * 查询并拉取该下游 MCP Server 提供的工具列表 (tools/list)。
     *
     * @return 响应式包含的工具定义列表
     */
    Mono<List<GovernedTool>> listTools();

    /**
     * 向下游 MCP Server 派发远程工具调用 (tools/call)。
     *
     * @param toolName 下游实际工具名称（已剥离网关前缀）
     * @param arguments 工具调用入参
     * @return 响应式包含的工具调用执行结果
     */
    Mono<McpInvocationResult> callTool(String toolName, Map<String, Object> arguments);

    /**
     * 健康检查与心跳探测。
     *
     * @return 响应式包含的健康状态，true 表示连通正常，false 表示异常
     */
    Mono<Boolean> ping();

    /**
     * 关闭出站客户端，释放底层的网络连接、长连接会话或子进程资源。
     */
    @Override
    void close();
}

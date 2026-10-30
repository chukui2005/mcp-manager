package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 下游服务工具列表提供器抽象。
 * 屏蔽下游不同协议（SSE、Streamable HTTP、STDIO 或模拟数据）的远程通信细节，
 * 为网关聚合层提供统一的响应式工具发现能力。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public interface UpstreamToolProvider {

    /**
     * 向指定的下游服务查询其暴露的原始工具清单（未经命名前缀重写与 ACL 过滤）。
     *
     * @param route 下游路由定义
     * @return 响应式包含的工具定义列表
     */
    Mono<List<GovernedTool>> listTools(RouteDefinition route);
}

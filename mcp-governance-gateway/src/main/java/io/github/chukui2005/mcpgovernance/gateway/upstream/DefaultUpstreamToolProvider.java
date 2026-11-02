package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的下游工具提供器实现。
 * 协同 {@link UpstreamClientPool} 动态拉取下游 MCP Server 的工具列表 (tools/list)。
 * 支持在单元测试或无远端长连接池阶段通过编程方式预设各路由下的工具清单，
 * 若未预设且无法连通真实客户端则默认提供基础的状态检测工具。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
@ConditionalOnMissingBean(name = "customUpstreamToolProvider")
public class DefaultUpstreamToolProvider implements UpstreamToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultUpstreamToolProvider.class);

    /**
     * 路由 ID 到工具列表的映射表（用于测试或静态预置）。
     */
    private final Map<String, List<GovernedTool>> toolsByRouteId = new ConcurrentHashMap<>();

    private final UpstreamClientPool clientPool;

    /**
     * 默认构造函数（无连接池注入）。
     */
    public DefaultUpstreamToolProvider() {
        this(null);
    }

    /**
     * 注入下游客户端连接池。
     *
     * @param clientPool 下游出站客户端池
     */
    @Autowired
    public DefaultUpstreamToolProvider(UpstreamClientPool clientPool) {
        this.clientPool = clientPool;
    }

    /**
     * 为指定路由 ID 预置工具列表（主要用于单元测试与模拟）。
     *
     * @param routeId 路由 ID
     * @param tools   工具定义列表
     */
    public void registerTools(String routeId, List<GovernedTool> tools) {
        if (routeId != null) {
            toolsByRouteId.put(routeId, tools != null ? new ArrayList<>(tools) : Collections.emptyList());
            log.debug("为下游路由 [{}] 预置了 {} 个工具", routeId, tools != null ? tools.size() : 0);
        }
    }

    /**
     * 清理所有预置的工具映射。
     */
    public void clearTools() {
        toolsByRouteId.clear();
    }

    @Override
    public Mono<List<GovernedTool>> listTools(RouteDefinition route) {
        if (route == null) {
            return Mono.just(Collections.emptyList());
        }

        String routeId = route.getId();

        // 1. 优先检查是否有针对该路由的手动注册/Mock 工具（保证测试用例优先）
        List<GovernedTool> registered = toolsByRouteId.get(routeId);
        if (registered != null) {
            return Mono.just(Collections.unmodifiableList(registered));
        }

        // 2. 若存在连接池且路由具有网络地址，则尝试调用真实的客户端拉取 tools/list
        if (clientPool != null && route.getProtocol() != null && route.getUri() != null && !route.getUri().trim().isEmpty()) {
            try {
                UpstreamClient client = clientPool.getClient(route);
                return client.listTools()
                        .onErrorResume(e -> {
                            log.warn("通过客户端拉取下游 [{}] 工具清单失败: {}，回退到默认工具", routeId, e.getMessage());
                            return fallbackTools(routeId);
                        });
            } catch (Exception e) {
                log.warn("获取下游客户端失败: {}，回退到默认工具", e.getMessage());
            }
        }

        // 3. 默认回退：生成一个基础的状态工具
        return fallbackTools(routeId);
    }

    private Mono<List<GovernedTool>> fallbackTools(String routeId) {
        GovernedTool defaultTool = GovernedTool.of(
                "status",
                "下游服务 [" + routeId + "] 的运行状态工具",
                Map.of("type", "object", "properties", Collections.emptyMap())
        );
        return Mono.just(List.of(defaultTool));
    }
}

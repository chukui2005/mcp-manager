package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的下游工具提供器实现（提供内存模拟或各下游注册的工具表）。
 * 支持在单元测试或无远端长连接池阶段通过编程方式预设各路由下的工具清单，
 * 若未预设则默认提供一个基础的 health/status 检测工具。
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

    /**
     * 默认构造函数。
     */
    public DefaultUpstreamToolProvider() {
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
        List<GovernedTool> registered = toolsByRouteId.get(routeId);
        if (registered != null) {
            return Mono.just(Collections.unmodifiableList(registered));
        }

        // 默认回退：生成一个基础的状态工具
        GovernedTool defaultTool = GovernedTool.of(
                "status",
                "下游服务 [" + routeId + "] 的运行状态工具",
                Map.of("type", "object", "properties", Collections.emptyMap())
        );

        return Mono.just(List.of(defaultTool));
    }
}

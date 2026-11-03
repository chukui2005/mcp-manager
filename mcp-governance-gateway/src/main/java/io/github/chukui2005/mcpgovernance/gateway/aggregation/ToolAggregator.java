package io.github.chukui2005.mcpgovernance.gateway.aggregation;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.policy.AclMode;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclPolicy;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 工具聚合器。
 * 负责并发向上游多个目标 Server 查询 tools/list 清单，
 * 按照各路由的前缀规则注入命名空间前缀（例如将下游 "read_file" 重命名为 "fs:read_file" 或 "fs_read_file"），
 * 并根据租户/下游特定的 RouteGovernanceConfig 进行 ACL 规则检查，被禁止的工具在发现视图中静默剔除，
 * 实现最小权限安全暴露原则（防 OWASP MCP02 权限蔓延）。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class ToolAggregator {

    private static final Logger log = LoggerFactory.getLogger(ToolAggregator.class);

    private final RouteManager routeManager;
    private final UpstreamToolProvider upstreamToolProvider;

    /**
     * 依赖注入构造函数。
     *
     * @param routeManager         路由管理器
     * @param upstreamToolProvider 下游工具提供器
     */
    public ToolAggregator(RouteManager routeManager, UpstreamToolProvider upstreamToolProvider) {
        this.routeManager = Objects.requireNonNull(routeManager, "routeManager 不能为 null");
        this.upstreamToolProvider = Objects.requireNonNull(upstreamToolProvider, "upstreamToolProvider 不能为 null");
    }

    /**
     * 响应式聚合所有已注册路由的工具清单，并应用命名空间前缀重写与 ACL 权限过滤。
     *
     * @return 聚合且重写后的安全工具定义列表
     */
    public Mono<List<GovernedTool>> aggregateTools() {
        List<RouteDefinition> routes = routeManager.getAllRoutes();
        if (routes.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return Flux.fromIterable(routes)
                .flatMap(this::fetchAndProcessRouteTools)
                .collectList()
                .map(nestedList -> {
                    List<GovernedTool> flatList = new ArrayList<>();
                    for (List<GovernedTool> list : nestedList) {
                        flatList.addAll(list);
                    }
                    return Collections.unmodifiableList(flatList);
                });
    }

    /**
     * 将 GovernedTool 列表格式化转换为符合 MCP JSON-RPC 2.0 规范的 Map 结构字典。
     * 格式如：[{ "name": "...", "description": "...", "inputSchema": { ... } }]。
     *
     * @param tools 聚合后的工具列表
     * @return 符合 MCP tools/list 规范的工具元数据列表
     */
    public List<Map<String, Object>> formatMcpTools(List<GovernedTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (GovernedTool tool : tools) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", tool.name());
            map.put("description", tool.description() != null ? tool.description() : "");
            map.put("inputSchema", tool.inputSchema() != null ? tool.inputSchema() : Collections.emptyMap());
            result.add(map);
        }
        return result;
    }

    /**
     * 拉取单个下游路由的工具并执行前缀注入与 ACL 过滤。
     * 若路由处于停用状态 (enabled == false)，直接跳过并不拉取工具。
     *
     * @param route 下游路由定义
     * @return 该路由下可见且带有命名空间的工具列表
     */
    private Mono<List<GovernedTool>> fetchAndProcessRouteTools(RouteDefinition route) {
        String routeId = route.getId();
        if (!route.isEnabled()) {
            log.debug("下游路由 [{}] 当前处于停用状态，跳过工具聚合", routeId);
            return Mono.just(Collections.emptyList());
        }

        String prefix = route.getPrefix();
        ToolAclPolicy aclPolicy = buildToolAclPolicy(route.getGovernance());

        return upstreamToolProvider.listTools(route)
                .map(rawTools -> {
                    List<GovernedTool> processed = new ArrayList<>();
                    for (GovernedTool raw : rawTools) {
                        String rawName = raw.name();
                        // 1. 根据下游治理配置的 ACL 进行可见性检查
                        if (!aclPolicy.isAllowed(rawName)) {
                            log.debug("工具 [{}] 被路由 [{}] 的 ACL 策略阻断，已从 tools/list 发现视图中剔除", rawName, routeId);
                            continue;
                        }

                        // 2. 注入命名空间前缀重命名
                        String aggregatedName = prefix + rawName;
                        GovernedTool governed = GovernedTool.of(
                                aggregatedName,
                                raw.description(),
                                raw.inputSchema(),
                                raw.riskTags(),
                                raw.severity()
                        );
                        processed.add(governed);
                    }
                    return processed;
                })
                .onErrorResume(e -> {
                    log.error("从下游路由 [{}] 拉取工具列表发生异常: {}", routeId, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * 根据路由治理配置构建 core 的 ToolAclPolicy 对象。
     *
     * @param governance 治理配置
     * @return 对应的 ToolAclPolicy 实例
     */
    private ToolAclPolicy buildToolAclPolicy(RouteGovernanceConfig governance) {
        if (governance == null) {
            // 未显式配置治理规则时，默认全部放行
            return new ToolAclPolicy(false, AclMode.WHITELIST, List.of(), List.of());
        }

        String modeStr = governance.getAclMode();
        AclMode mode = "BLACKLIST".equalsIgnoreCase(modeStr) ? AclMode.BLACKLIST : AclMode.WHITELIST;
        List<String> allowed = governance.getAllowedTools() != null ? governance.getAllowedTools() : List.of();
        List<String> denied = governance.getDeniedTools() != null ? governance.getDeniedTools() : List.of();

        boolean enabled = modeStr != null || !allowed.isEmpty() || !denied.isEmpty();
        return new ToolAclPolicy(enabled, mode, allowed, denied);
    }
}

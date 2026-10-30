package io.github.chukui2005.mcpgovernance.gateway.route;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 路由管理器组件。
 * 负责下游路由定义的内存维护、冲突校验、按路由 ID 索引以及基于工具名前缀进行路由匹配解析。
 * 具备线程安全的动态路由增删查能力。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class RouteManager {

    private static final Logger log = LoggerFactory.getLogger(RouteManager.class);

    /**
     * 路由 ID 索引表（ID -> RouteDefinition）。
     */
    private final Map<String, RouteDefinition> routesById = new ConcurrentHashMap<>();

    /**
     * 按前缀长度降序排列的路由定义列表，确保最长前缀优先匹配。
     */
    private final List<RouteDefinition> sortedRoutes = new CopyOnWriteArrayList<>();

    /**
     * 默认构造函数。
     */
    public RouteManager() {
    }

    /**
     * 注册单个路由定义。
     *
     * @param route 路由定义
     * @throws IllegalArgumentException 当路由 ID 或前缀冲突、或关键属性为空时抛出
     */
    public synchronized void registerRoute(RouteDefinition route) {
        Objects.requireNonNull(route, "route 不能为 null");
        String id = route.getId();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("路由 ID 不能为空");
        }
        String prefix = route.getPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("路由前缀 prefix 不能为空: id=" + id);
        }
        if (routesById.containsKey(id)) {
            throw new IllegalArgumentException("已存在相同 ID 的路由: " + id);
        }
        for (RouteDefinition existing : sortedRoutes) {
            if (existing.getPrefix().equals(prefix)) {
                throw new IllegalArgumentException("已存在相同前缀 prefix 的路由: " + prefix + " (冲突路由: " + existing.getId() + ")");
            }
        }

        routesById.put(id, route);
        rebuildSortedRoutes();
        log.info("成功注册下游 MCP 路由: id={}, protocol={}, prefix={}, uri={}",
                route.getId(), route.getProtocol(), route.getPrefix(), route.getUri());
    }

    /**
     * 批量加载并注册路由列表。
     *
     * @param routeList 路由定义列表
     */
    public synchronized void registerRoutes(List<RouteDefinition> routeList) {
        if (routeList == null || routeList.isEmpty()) {
            return;
        }
        for (RouteDefinition route : routeList) {
            registerRoute(route);
        }
    }

    /**
     * 注销单个路由定义。
     *
     * @param routeId 路由 ID
     * @return 移除成功的路由定义，不存在则返回 empty
     */
    public synchronized Optional<RouteDefinition> unregisterRoute(String routeId) {
        if (routeId == null) {
            return Optional.empty();
        }
        RouteDefinition removed = routesById.remove(routeId);
        if (removed != null) {
            rebuildSortedRoutes();
            log.info("注销下游 MCP 路由: id={}", routeId);
            return Optional.of(removed);
        }
        return Optional.empty();
    }

    /**
     * 根据路由 ID 获取对应的路由定义。
     *
     * @param routeId 路由 ID
     * @return 匹配的路由定义
     */
    public Optional<RouteDefinition> getRouteById(String routeId) {
        if (routeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(routesById.get(routeId));
    }

    /**
     * 获取当前所有已注册的路由定义不可变视图。
     *
     * @return 路由定义列表
     */
    public List<RouteDefinition> getAllRoutes() {
        return Collections.unmodifiableList(new ArrayList<>(sortedRoutes));
    }

    /**
     * 根据客户端调用的聚合工具名称匹配对应的下游路由。
     * 采用最长前缀优先匹配原则，剥离前缀后返回下游实际工具名称。
     *
     * @param aggregatedToolName 聚合工具名称，例如 "k8s_get_pods"
     * @return 匹配结果包装对象；若未匹配到任何前缀则返回 empty
     */
    public Optional<RouteMatchResult> matchByToolName(String aggregatedToolName) {
        if (aggregatedToolName == null || aggregatedToolName.trim().isEmpty()) {
            return Optional.empty();
        }

        for (RouteDefinition route : sortedRoutes) {
            String prefix = route.getPrefix();
            if (aggregatedToolName.startsWith(prefix)) {
                String targetToolName = aggregatedToolName.substring(prefix.length());
                if (!targetToolName.isEmpty()) {
                    return Optional.of(new RouteMatchResult(route, targetToolName, prefix));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 清空当前所有注册的路由。
     */
    public synchronized void clear() {
        routesById.clear();
        sortedRoutes.clear();
        log.info("清空所有下游 MCP 路由");
    }

    /**
     * 获取当前注册的路由总数。
     *
     * @return 路由数量
     */
    public int size() {
        return routesById.size();
    }

    /**
     * 重建按前缀长度降序排列的路由列表。
     */
    private void rebuildSortedRoutes() {
        List<RouteDefinition> list = new ArrayList<>(routesById.values());
        list.sort(Comparator.comparingInt((RouteDefinition r) -> r.getPrefix().length()).reversed());
        sortedRoutes.clear();
        sortedRoutes.addAll(list);
    }
}

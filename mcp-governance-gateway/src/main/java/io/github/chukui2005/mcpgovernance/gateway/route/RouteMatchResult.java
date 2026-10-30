package io.github.chukui2005.mcpgovernance.gateway.route;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;

import java.util.Objects;

/**
 * 路由匹配结果值对象。
 * 包含匹配到的路由定义、剥离前缀后的真实工具名称以及提取的前缀标识。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class RouteMatchResult {

    /**
     * 匹配到的路由定义。
     */
    private final RouteDefinition routeDefinition;

    /**
     * 剥离前缀后的真实下游工具名称。
     */
    private final String targetToolName;

    /**
     * 匹配到的前缀字符串。
     */
    private final String matchedPrefix;

    /**
     * 构造路由匹配结果对象。
     *
     * @param routeDefinition 路由定义
     * @param targetToolName  目标工具名称
     * @param matchedPrefix   匹配到的前缀
     */
    public RouteMatchResult(RouteDefinition routeDefinition, String targetToolName, String matchedPrefix) {
        this.routeDefinition = Objects.requireNonNull(routeDefinition, "routeDefinition 不能为空");
        this.targetToolName = Objects.requireNonNull(targetToolName, "targetToolName 不能为空");
        this.matchedPrefix = Objects.requireNonNull(matchedPrefix, "matchedPrefix 不能为空");
    }

    /**
     * 获取匹配到的路由定义。
     *
     * @return 路由定义
     */
    public RouteDefinition getRouteDefinition() {
        return routeDefinition;
    }

    /**
     * 获取剥离前缀后的真实下游工具名称。
     *
     * @return 目标工具名称
     */
    public String getTargetToolName() {
        return targetToolName;
    }

    /**
     * 获取匹配到的前缀字符串。
     *
     * @return 匹配前缀
     */
    public String getMatchedPrefix() {
        return matchedPrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteMatchResult that = (RouteMatchResult) o;
        return Objects.equals(routeDefinition, that.routeDefinition) &&
                Objects.equals(targetToolName, that.targetToolName) &&
                Objects.equals(matchedPrefix, that.matchedPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeDefinition, targetToolName, matchedPrefix);
    }
}

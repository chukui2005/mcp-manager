package io.github.chukui2005.mcpgovernance.gateway.route;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RouteManager 单元测试。
 * 验证路由注册、重复校验、按 ID 查询、注销以及按工具名前缀最长匹配等核心逻辑。
 *
 * @author chukui2005
 */
class RouteManagerTest {

    private RouteManager routeManager;

    @BeforeEach
    void setUp() {
        routeManager = new RouteManager();
    }

    @Test
    @DisplayName("注册路由与根据 ID 查询")
    void testRegisterAndGetById() {
        RouteDefinition route = new RouteDefinition("k8s-server", UpstreamProtocol.HTTP, "k8s_", "http://127.0.0.1:9001/mcp");
        routeManager.registerRoute(route);

        assertThat(routeManager.size()).isEqualTo(1);
        Optional<RouteDefinition> retrieved = routeManager.getRouteById("k8s-server");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPrefix()).isEqualTo("k8s_");
        assertThat(retrieved.get().getProtocol()).isEqualTo(UpstreamProtocol.HTTP);
    }

    @Test
    @DisplayName("注册路由时校验重复 ID 和重复前缀")
    void testDuplicateValidation() {
        RouteDefinition route1 = new RouteDefinition("db-server", UpstreamProtocol.HTTP, "db_", "http://127.0.0.1:9002/mcp");
        routeManager.registerRoute(route1);

        RouteDefinition routeDuplicateId = new RouteDefinition("db-server", UpstreamProtocol.SSE, "db2_", "http://127.0.0.1:9003/sse");
        assertThatThrownBy(() -> routeManager.registerRoute(routeDuplicateId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在相同 ID 的路由: db-server");

        RouteDefinition routeDuplicatePrefix = new RouteDefinition("other-db", UpstreamProtocol.SSE, "db_", "http://127.0.0.1:9004/sse");
        assertThatThrownBy(() -> routeManager.registerRoute(routeDuplicatePrefix))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在相同前缀 prefix 的路由: db_");
    }

    @Test
    @DisplayName("按工具名前缀最长匹配与去除前缀")
    void testMatchByToolNameLongestPrefixFirst() {
        RouteDefinition shortPrefix = new RouteDefinition("fs-base", UpstreamProtocol.HTTP, "fs_", "http://127.0.0.1:9005");
        RouteDefinition longPrefix = new RouteDefinition("fs-ext", UpstreamProtocol.HTTP, "fs_ext_", "http://127.0.0.1:9006");

        routeManager.registerRoute(shortPrefix);
        routeManager.registerRoute(longPrefix);

        // 匹配长前缀
        Optional<RouteMatchResult> matchLong = routeManager.matchByToolName("fs_ext_read_file");
        assertThat(matchLong).isPresent();
        assertThat(matchLong.get().getRouteDefinition().getId()).isEqualTo("fs-ext");
        assertThat(matchLong.get().getTargetToolName()).isEqualTo("read_file");
        assertThat(matchLong.get().getMatchedPrefix()).isEqualTo("fs_ext_");

        // 匹配短前缀
        Optional<RouteMatchResult> matchShort = routeManager.matchByToolName("fs_list_directory");
        assertThat(matchShort).isPresent();
        assertThat(matchShort.get().getRouteDefinition().getId()).isEqualTo("fs-base");
        assertThat(matchShort.get().getTargetToolName()).isEqualTo("list_directory");
        assertThat(matchShort.get().getMatchedPrefix()).isEqualTo("fs_");

        // 无匹配
        Optional<RouteMatchResult> matchNone = routeManager.matchByToolName("unknown_tool");
        assertThat(matchNone).isEmpty();

        // 刚好是前缀但无工具名
        Optional<RouteMatchResult> matchExactPrefix = routeManager.matchByToolName("fs_");
        assertThat(matchExactPrefix).isEmpty();
    }

    @Test
    @DisplayName("批量注册、注销与清空")
    void testBatchOperations() {
        RouteDefinition r1 = new RouteDefinition("r1", UpstreamProtocol.HTTP, "r1_", "http://127.0.0.1:8001");
        RouteDefinition r2 = new RouteDefinition("r2", UpstreamProtocol.SSE, "r2_", "http://127.0.0.1:8002");

        routeManager.registerRoutes(List.of(r1, r2));
        assertThat(routeManager.size()).isEqualTo(2);

        Optional<RouteDefinition> removed = routeManager.unregisterRoute("r1");
        assertThat(removed).isPresent();
        assertThat(routeManager.size()).isEqualTo(1);
        assertThat(routeManager.getRouteById("r1")).isEmpty();

        routeManager.clear();
        assertThat(routeManager.size()).isEqualTo(0);
        assertThat(routeManager.getAllRoutes()).isEmpty();
    }

    @Test
    @DisplayName("测试动态更新工具 ACL 策略并实时生效")
    void testDynamicUpdateToolAcl() {
        RouteDefinition route = new RouteDefinition();
        route.setId("fs-svc");
        route.setPrefix("fs_");
        route.setProtocol(UpstreamProtocol.HTTP);
        route.setUri("http://127.0.0.1:8080/mcp");
        routeManager.registerRoute(route);

        // 1. 动态阻断 delete_file
        String status1 = routeManager.updateToolAcl("fs-svc", "delete_file", "DENY");
        assertThat(status1).isEqualTo("DENIED");

        RouteDefinition updated = routeManager.getRouteById("fs-svc").orElseThrow();
        assertThat(updated.getGovernance().getDeniedTools()).contains("delete_file");
        assertThat(updated.getGovernance().getAllowedTools()).doesNotContain("delete_file");

        // 2. 动态放行 delete_file
        String status2 = routeManager.updateToolAcl("fs-svc", "delete_file", "ALLOW");
        assertThat(status2).isEqualTo("ALLOWED");
        assertThat(updated.getGovernance().getAllowedTools()).contains("delete_file");
        assertThat(updated.getGovernance().getDeniedTools()).doesNotContain("delete_file");

        // 3. 动态重置 delete_file
        String status3 = routeManager.updateToolAcl("fs-svc", "delete_file", "RESET");
        assertThat(status3).isEqualTo("ALLOWED");
        assertThat(updated.getGovernance().getAllowedTools()).doesNotContain("delete_file");
        assertThat(updated.getGovernance().getDeniedTools()).doesNotContain("delete_file");
    }
}

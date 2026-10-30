package io.github.chukui2005.mcpgovernance.gateway.aggregation;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.DefaultUpstreamToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolAggregator 单元测试。
 * 验证多下游 tools/list 聚合、命名空间前缀注入、租户 ACL 白名单与黑名单过滤，以及格式化为 MCP 格式。
 *
 * @author chukui2005
 */
class ToolAggregatorTest {

    private RouteManager routeManager;
    private DefaultUpstreamToolProvider upstreamToolProvider;
    private ToolAggregator aggregator;

    @BeforeEach
    void setUp() {
        routeManager = new RouteManager();
        upstreamToolProvider = new DefaultUpstreamToolProvider();
        aggregator = new ToolAggregator(routeManager, upstreamToolProvider);
    }

    @Test
    @DisplayName("测试空路由列表时聚合返回空工具集")
    void testAggregateToolsEmpty() {
        StepVerifier.create(aggregator.aggregateTools())
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    @DisplayName("测试多下游工具命名空间前缀自动重写")
    void testNamespacePrefixing() {
        RouteDefinition k8sRoute = new RouteDefinition("k8s", UpstreamProtocol.HTTP, "k8s_", "http://k8s");
        RouteDefinition dbRoute = new RouteDefinition("db", UpstreamProtocol.STDIO, "db:", "stdio");
        routeManager.registerRoute(k8sRoute);
        routeManager.registerRoute(dbRoute);

        GovernedTool k8sPod = GovernedTool.of("get_pods", "List pods", Map.of("type", "object"));
        GovernedTool dbQuery = GovernedTool.of("query", "SQL query", Map.of("type", "object"));

        upstreamToolProvider.registerTools("k8s", List.of(k8sPod));
        upstreamToolProvider.registerTools("db", List.of(dbQuery));

        StepVerifier.create(aggregator.aggregateTools())
                .assertNext(tools -> {
                    assertThat(tools).hasSize(2);
                    List<String> names = tools.stream().map(GovernedTool::name).toList();
                    assertThat(names).containsExactlyInAnyOrder("k8s_get_pods", "db:query");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("测试租户 ACL 白名单过滤（仅暴露允许工具，防 OWASP MCP02 权限蔓延）")
    void testAclWhitelistFiltering() {
        RouteDefinition k8sRoute = new RouteDefinition("k8s", UpstreamProtocol.HTTP, "k8s_", "http://k8s");
        RouteGovernanceConfig governance = new RouteGovernanceConfig();
        governance.setAclMode("WHITELIST");
        governance.setAllowedTools(List.of("list_*", "get_node"));
        governance.setDeniedTools(List.of("list_secrets")); // 黑名单优先阻断
        k8sRoute.setGovernance(governance);

        routeManager.registerRoute(k8sRoute);

        GovernedTool t1 = GovernedTool.of("list_pods", "desc", Map.of("type", "object"));
        GovernedTool t2 = GovernedTool.of("list_secrets", "desc", Map.of("type", "object"));
        GovernedTool t3 = GovernedTool.of("get_node", "desc", Map.of("type", "object"));
        GovernedTool t4 = GovernedTool.of("delete_all", "desc", Map.of("type", "object")); // 不在白名单

        upstreamToolProvider.registerTools("k8s", List.of(t1, t2, t3, t4));

        StepVerifier.create(aggregator.aggregateTools())
                .assertNext(tools -> {
                    assertThat(tools).hasSize(2);
                    List<String> names = tools.stream().map(GovernedTool::name).toList();
                    // list_pods 与 get_node 放行，list_secrets 命中黑名单剔除，delete_all 未在白名单剔除
                    assertThat(names).containsExactlyInAnyOrder("k8s_list_pods", "k8s_get_node");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("测试租户 ACL 黑名单模式过滤")
    void testAclBlacklistFiltering() {
        RouteDefinition dbRoute = new RouteDefinition("db", UpstreamProtocol.HTTP, "db:", "http://db");
        RouteGovernanceConfig governance = new RouteGovernanceConfig();
        governance.setAclMode("BLACKLIST");
        governance.setDeniedTools(List.of("drop_*", "truncate"));
        dbRoute.setGovernance(governance);

        routeManager.registerRoute(dbRoute);

        GovernedTool t1 = GovernedTool.of("select", "desc", Map.of("type", "object"));
        GovernedTool t2 = GovernedTool.of("insert", "desc", Map.of("type", "object"));
        GovernedTool t3 = GovernedTool.of("drop_database", "desc", Map.of("type", "object"));

        upstreamToolProvider.registerTools("db", List.of(t1, t2, t3));

        StepVerifier.create(aggregator.aggregateTools())
                .assertNext(tools -> {
                    assertThat(tools).hasSize(2);
                    List<String> names = tools.stream().map(GovernedTool::name).toList();
                    assertThat(names).containsExactlyInAnyOrder("db:select", "db:insert");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("测试格式化为 MCP 规范 tools/list 字典结构")
    void testFormatMcpTools() {
        GovernedTool tool = GovernedTool.of("k8s_status", "Health check", Map.of("type", "object"));

        List<Map<String, Object>> formatted = aggregator.formatMcpTools(List.of(tool));
        assertThat(formatted).hasSize(1);
        Map<String, Object> map = formatted.get(0);
        assertThat(map.get("name")).isEqualTo("k8s_status");
        assertThat(map.get("description")).isEqualTo("Health check");
        assertThat(map.get("inputSchema")).isInstanceOf(Map.class);
    }
}

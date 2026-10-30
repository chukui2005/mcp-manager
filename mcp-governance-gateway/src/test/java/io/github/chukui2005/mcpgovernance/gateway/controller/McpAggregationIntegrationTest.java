package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgovernance.gateway.McpGatewayApplication;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.DefaultUpstreamToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * McpAggregationIntegrationTest 聚合端点与租户 ACL 隔离集成测试。
 * 端到端验证：
 * 1. 多下游 tools/list 聚合发现与命名空间前缀重写；
 * 2. 租户 ACL 白名单与黑名单过滤下无权限工具自动隐匿（防 OWASP MCP02 权限蔓延）；
 * 3. 动态注册下游后 tools/list 聚合视图即时生效；
 * 4. tools/call 精确路由分发与执行。
 *
 * @author chukui2005
 */
@SpringBootTest(classes = McpGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class McpAggregationIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteManager routeManager;

    @Autowired
    private DefaultUpstreamToolProvider upstreamToolProvider;

    @BeforeEach
    void setUp() {
        routeManager.clear();
        upstreamToolProvider.clearTools();
    }

    @Test
    @DisplayName("集成测试：多下游 tools/list 聚合与租户 ACL 深度过滤")
    void testAggregatedToolsListWithTenantAcl() {
        // 1. 注册 k8s-server 路由，配置 WHITELIST ACL 策略
        RouteDefinition k8sRoute = new RouteDefinition(
                "k8s-server",
                UpstreamProtocol.HTTP,
                "k8s_",
                "http://127.0.0.1:9091/mcp"
        );
        RouteGovernanceConfig k8sGov = new RouteGovernanceConfig();
        k8sGov.setAclMode("WHITELIST");
        k8sGov.setAllowedTools(List.of("list_*", "get_pods"));
        k8sGov.setDeniedTools(List.of("list_secrets")); // 黑名单优先
        k8sRoute.setGovernance(k8sGov);
        routeManager.registerRoute(k8sRoute);

        // 2. 注册 db-server 路由，配置 BLACKLIST ACL 策略
        RouteDefinition dbRoute = new RouteDefinition(
                "db-server",
                UpstreamProtocol.STDIO,
                "db:",
                "stdio"
        );
        RouteGovernanceConfig dbGov = new RouteGovernanceConfig();
        dbGov.setAclMode("BLACKLIST");
        dbGov.setDeniedTools(List.of("drop_*", "truncate"));
        dbRoute.setGovernance(dbGov);
        routeManager.registerRoute(dbRoute);

        // 3. 预设下游返回的原始工具
        GovernedTool k8sPod = GovernedTool.of("get_pods", "Get Pods", Map.of("type", "object"));
        GovernedTool k8sNodes = GovernedTool.of("list_nodes", "List Nodes", Map.of("type", "object"));
        GovernedTool k8sSecrets = GovernedTool.of("list_secrets", "List Secrets", Map.of("type", "object"));
        GovernedTool k8sDelete = GovernedTool.of("delete_cluster", "Delete Cluster", Map.of("type", "object"));
        upstreamToolProvider.registerTools("k8s-server", List.of(k8sPod, k8sNodes, k8sSecrets, k8sDelete));

        GovernedTool dbSelect = GovernedTool.of("select", "Execute Query", Map.of("type", "object"));
        GovernedTool dbDrop = GovernedTool.of("drop_table", "Drop Table", Map.of("type", "object"));
        upstreamToolProvider.registerTools("db-server", List.of(dbSelect, dbDrop));

        // 4. 发起 tools/list JSON-RPC 请求
        JsonRpcRequest request = new JsonRpcRequest(1001, "tools/list", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(1001)
                // 预期聚合保留工具：k8s_get_pods, k8s_list_nodes, db:select（共3个）
                // 预期过滤工具：k8s_list_secrets (黑名单阻断), k8s_delete_cluster (未在白名单), db:drop_table (黑名单阻断)
                .jsonPath("$.result.tools").value(hasSize(3))
                .jsonPath("$.result.tools[*].name").value(hasItems("k8s_get_pods", "k8s_list_nodes", "db:select"))
                .jsonPath("$.result.tools[*].name").value(not(hasItems("k8s_list_secrets", "k8s_delete_cluster", "db:drop_table")));
    }

    @Test
    @DisplayName("集成测试：调用被聚合的工具 tools/call 正常派发执行")
    void testToolsCallAggregatedSuccess() {
        RouteDefinition fsRoute = new RouteDefinition(
                "fs-server",
                UpstreamProtocol.HTTP,
                "fs_",
                "http://127.0.0.1:9095/mcp"
        );
        routeManager.registerRoute(fsRoute);

        Map<String, Object> params = Map.of(
                "name", "fs_read_file",
                "arguments", Map.of("path", "/workspace/test.txt")
        );
        JsonRpcRequest request = new JsonRpcRequest(2001, "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(2001)
                .jsonPath("$.result.isError").isEqualTo(false)
                .jsonPath("$.result.content[0].type").isEqualTo("text");
    }
}

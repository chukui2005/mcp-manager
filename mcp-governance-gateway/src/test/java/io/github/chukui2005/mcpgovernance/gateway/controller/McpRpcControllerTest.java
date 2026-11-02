package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgovernance.gateway.McpGatewayApplication;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * McpRpcController 响应式集成测试。
 * 验证 POST /mcp/v1/rpc 协议端点处理：initialize、tools/list、tools/call 以及异常处理逻辑。
 *
 * @author chukui2005
 */
@SpringBootTest(classes = McpGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class McpRpcControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteManager routeManager;

    @BeforeEach
    void setUp() {
        routeManager.clear();
        RouteDefinition route = new RouteDefinition(
                "k8s-server",
                UpstreamProtocol.HTTP,
                "k8s_",
                "http://127.0.0.1:9091/mcp"
        );
        routeManager.registerRoute(route);
    }

    @Test
    @DisplayName("测试 MCP 协议握手 initialize")
    void testInitialize() {
        JsonRpcRequest request = new JsonRpcRequest(1, "initialize", Map.of("protocolVersion", "2024-11-05"));

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.result.protocolVersion").isEqualTo("2024-11-05")
                .jsonPath("$.result.serverInfo.name").isEqualTo("mcp-governance-gateway")
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    @DisplayName("测试 MCP 工具聚合发现 tools/list")
    void testToolsList() {
        JsonRpcRequest request = new JsonRpcRequest(2, "tools/list", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.result.tools.length()").value(greaterThanOrEqualTo(1))
                .jsonPath("$.result.tools[0].name").isEqualTo("k8s_status")
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    @DisplayName("测试 MCP 工具调用分发 tools/call (当匹配路由但无实体服务时如实返回错误)")
    void testToolsCallSuccess() {
        Map<String, Object> params = Map.of(
                "name", "k8s_get_pods",
                "arguments", Map.of("namespace", "default")
        );
        JsonRpcRequest request = new JsonRpcRequest("call-101", "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo("call-101")
                .jsonPath("$.result.content").isArray()
                .jsonPath("$.result.isError").isEqualTo(true)
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    @DisplayName("测试 MCP 工具调用 tools/call 缺少路由匹配报错")
    void testToolsCallRouteNotFound() {
        Map<String, Object> params = Map.of(
                "name", "unregistered_tool",
                "arguments", Map.of()
        );
        JsonRpcRequest request = new JsonRpcRequest("call-102", "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo("call-102")
                .jsonPath("$.error").value(notNullValue())
                .jsonPath("$.error.code").isEqualTo(-32601)
                .jsonPath("$.error.message").value(equalTo("未找到工具对应的下游路由: unregistered_tool"));
    }

    @Test
    @DisplayName("测试 MCP 保活探针 ping")
    void testPing() {
        JsonRpcRequest request = new JsonRpcRequest(10, "ping", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(10)
                .jsonPath("$.result.status").isEqualTo("ok")
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    @DisplayName("测试客户端通知 notifications/initialized 返回 204 No Content")
    void testNotificationInitialized() {
        JsonRpcRequest request = new JsonRpcRequest(null, "notifications/initialized", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    @DisplayName("测试未知方法调用返回 Method not found")
    void testUnknownMethod() {
        JsonRpcRequest request = new JsonRpcRequest(999, "unknown_method", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(999)
                .jsonPath("$.error").value(notNullValue())
                .jsonPath("$.error.code").isEqualTo(-32601);
    }
}

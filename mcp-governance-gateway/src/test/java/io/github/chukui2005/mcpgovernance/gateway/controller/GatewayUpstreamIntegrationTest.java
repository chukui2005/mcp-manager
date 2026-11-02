package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClientPool;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关出站客户端集成测试。
 * 使用 MockWebServer 模拟真实的远端 HTTP MCP Server，
 * 验证从网关入口 POST /mcp/v1/rpc 到出站客户端池、真实远端调用的完整链路。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayUpstreamIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteManager routeManager;

    @Autowired
    private UpstreamClientPool upstreamClientPool;

    private MockWebServer mockWebServer;
    private String mockServerUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockServerUrl = mockWebServer.url("/mcp").toString();
        routeManager.clear();
        upstreamClientPool.closeAll();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        routeManager.clear();
        upstreamClientPool.closeAll();
    }

    @Test
    @DisplayName("端到端测试：网关接收 tools/call 并由 HttpUpstreamClient 派发至真实远端 Server")
    void testEndToEndToolsCallViaClientPool() {
        // 注册测试下游路由
        RouteDefinition route = new RouteDefinition(
                "remote-fs",
                UpstreamProtocol.HTTP,
                "fs_",
                mockServerUrl
        );
        routeManager.registerRoute(route);

        // Mock 远端下游返回的 tools/call JSON-RPC 响应
        String remoteResponseBody = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": "File content from remote server",
                    "bytesRead": 32
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(remoteResponseBody));

        // 发送给网关的请求
        Map<String, Object> params = Map.of(
                "name", "fs_read_file",
                "arguments", Map.of("path", "/data/app.log")
        );
        JsonRpcRequest request = new JsonRpcRequest(8888, "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(8888)
                .jsonPath("$.result.isError").isEqualTo(false)
                .jsonPath("$.result.content[0].type").isEqualTo("text")
                .jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("File content from remote server"));

        // 验证客户端连接池中已缓存该连接
        assertThat(upstreamClientPool.size()).isEqualTo(1);
        assertThat(upstreamClientPool.getExistingClient("remote-fs")).isNotNull();
    }

    @Test
    @DisplayName("端到端测试：网关接收 tools/list 并通过连接池向远端拉取最新工具清单")
    void testEndToEndToolsListViaClientPool() {
        RouteDefinition route = new RouteDefinition(
                "remote-k8s",
                UpstreamProtocol.HTTP,
                "k8s_",
                mockServerUrl
        );
        routeManager.registerRoute(route);

        // Mock 远端返回 tools/list
        String remoteToolsList = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "tools": [
                      {
                        "name": "list_pods",
                        "description": "List pods in cluster",
                        "inputSchema": { "type": "object" }
                      }
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(remoteToolsList));

        JsonRpcRequest request = new JsonRpcRequest(9999, "tools/list", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(9999)
                .jsonPath("$.result.tools").isArray()
                .jsonPath("$.result.tools[0].name").isEqualTo("k8s_list_pods")
                .jsonPath("$.result.tools[0].description").isEqualTo("List pods in cluster");
    }
}

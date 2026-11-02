package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamAuthConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.upstream.auth.TokenRefResolver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HttpUpstreamClient} 单元测试（基于 MockWebServer 模拟远端 HTTP MCP Server）。
 *
 * @author chukui2005
 * @since 0.1.0
 */
class HttpUpstreamClientTest {

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private TokenRefResolver tokenRefResolver;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
        tokenRefResolver = new TokenRefResolver();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("测试 HttpUpstreamClient 成功拉取工具列表 tools/list 并注入鉴权头")
    void testListToolsSuccess() throws Exception {
        String baseUrl = mockWebServer.url("/mcp").toString();

        RouteDefinition route = new RouteDefinition("test-http", UpstreamProtocol.HTTP, "test_", baseUrl);
        UpstreamAuthConfig auth = new UpstreamAuthConfig();
        auth.setType("bearer");
        auth.setHeaderName("X-Custom-Auth");
        auth.setTokenRef("${PATH}"); // 使用已有系统环境变量测试
        route.setAuth(auth);

        String jsonRpcResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "tools": [
                      {
                        "name": "echo",
                        "description": "Echo back message",
                        "inputSchema": {
                          "type": "object",
                          "properties": { "msg": { "type": "string" } }
                        }
                      }
                    ]
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(jsonRpcResponse));

        HttpUpstreamClient client = new HttpUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        List<GovernedTool> tools = client.listTools().block();
        assertThat(tools).isNotNull().hasSize(1);
        GovernedTool tool = tools.get(0);
        assertThat(tool.name()).isEqualTo("echo");
        assertThat(tool.description()).isEqualTo("Echo back message");

        RecordedRequest recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        if (System.getenv("PATH") != null) {
            assertThat(recorded.getHeader("X-Custom-Auth")).startsWith("Bearer ");
        }

        client.close();
    }

    @Test
    @DisplayName("测试 HttpUpstreamClient 远程调用工具 tools/call 成功返回结果")
    void testCallToolSuccess() throws Exception {
        String baseUrl = mockWebServer.url("/mcp").toString();
        RouteDefinition route = new RouteDefinition("test-http", UpstreamProtocol.HTTP, "test_", baseUrl);

        String jsonRpcResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "output": "hello world"
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(jsonRpcResponse));

        HttpUpstreamClient client = new HttpUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        McpInvocationResult result = client.callTool("echo", Map.of("msg", "hello")).block();
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.result()).isInstanceOf(Map.class);
        Map<?, ?> resultMap = (Map<?, ?>) result.result();
        assertThat(resultMap.get("output")).isEqualTo("hello world");

        RecordedRequest recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getBody().readUtf8()).contains("\"method\":\"tools/call\"");

        client.close();
    }

    @Test
    @DisplayName("测试 HttpUpstreamClient 下游响应 JSON-RPC 业务错误时的妥善捕获")
    void testCallToolRpcError() {
        String baseUrl = mockWebServer.url("/mcp").toString();
        RouteDefinition route = new RouteDefinition("test-http", UpstreamProtocol.HTTP, "test_", baseUrl);

        String errorResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "error": {
                    "code": -32602,
                    "message": "Invalid params"
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(errorResponse));

        HttpUpstreamClient client = new HttpUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        McpInvocationResult result = client.callTool("echo", Map.of()).block();
        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid params");

        client.close();
    }

    @Test
    @DisplayName("测试 HttpUpstreamClient 客户端关闭后拒绝请求")
    void testClientClosed() {
        String baseUrl = mockWebServer.url("/mcp").toString();
        RouteDefinition route = new RouteDefinition("test-http", UpstreamProtocol.HTTP, "test_", baseUrl);

        HttpUpstreamClient client = new HttpUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        client.close();
        assertThatThrownBy(() -> client.listTools().block())
                .hasMessageContaining("已关闭");
    }
}

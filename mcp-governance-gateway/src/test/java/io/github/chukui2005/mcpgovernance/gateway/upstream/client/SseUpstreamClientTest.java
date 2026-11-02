package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.upstream.auth.TokenRefResolver;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SseUpstreamClient} 单元测试（基于 MockWebServer 模拟 SSE 长连接与响应）。
 *
 * @author chukui2005
 * @since 0.1.0
 */
class SseUpstreamClientTest {

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
    @DisplayName("测试 SseUpstreamClient 订阅 SSE 事件并执行 tools/list")
    void testSseListTools() {
        String baseUrl = mockWebServer.url("/sse").toString();
        RouteDefinition route = new RouteDefinition("test-sse", UpstreamProtocol.SSE, "sse_", baseUrl);

        // 1. SSE 订阅响应（返回初始 endpoint 事件）
        String sseStreamBody = "event: endpoint\ndata: " + baseUrl + "\n\n";
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseStreamBody));

        // 2. tools/list 的 POST 响应
        String jsonRpcResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "tools": [
                      {
                        "name": "sse_tool",
                        "description": "SSE remote tool",
                        "inputSchema": { "type": "object" }
                      }
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(jsonRpcResponse));

        SseUpstreamClient client = new SseUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        List<GovernedTool> tools = client.listTools().block();
        assertThat(tools).isNotNull().hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("sse_tool");

        client.close();
    }

    @Test
    @DisplayName("测试 SseUpstreamClient 远程调用 tools/call 正常响应")
    void testSseCallTool() {
        String baseUrl = mockWebServer.url("/sse").toString();
        RouteDefinition route = new RouteDefinition("test-sse", UpstreamProtocol.SSE, "sse_", baseUrl);

        // SSE 订阅响应
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: endpoint\ndata: " + baseUrl + "\n\n"));

        // tools/call POST 响应
        String callResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "status": "stream-done"
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(callResponse));

        SseUpstreamClient client = new SseUpstreamClient(
                route,
                tokenRefResolver,
                WebClient.builder(),
                objectMapper,
                5000
        );

        McpInvocationResult result = client.callTool("sse_tool", Map.of()).block();
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();

        client.close();
    }

    @Test
    @DisplayName("测试 SseUpstreamClient 关闭后抛出异常")
    void testClientClosed() {
        String baseUrl = mockWebServer.url("/sse").toString();
        RouteDefinition route = new RouteDefinition("test-sse", UpstreamProtocol.SSE, "sse_", baseUrl);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(""));

        SseUpstreamClient client = new SseUpstreamClient(
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

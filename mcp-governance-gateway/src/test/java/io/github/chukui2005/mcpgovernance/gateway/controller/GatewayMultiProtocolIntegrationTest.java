package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.StdioCommandConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClientPool;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关全协议（HTTP、SSE、STDIO）混合多路由共存端到端集成测试。
 * 验证统一聚合发现（tools/list）与跨协议路由分发（tools/call）的完整可用性。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayMultiProtocolIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteManager routeManager;

    @Autowired
    private UpstreamClientPool upstreamClientPool;

    private static MockWebServer mockHttpServer;
    private static Path tempDir;
    private static Path stdioServerSourceFile;
    private static String javaBin;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockHttpServer = new MockWebServer();
        mockHttpServer.start();

        tempDir = Files.createTempDirectory("mcp-multi-proto-");
        javaBin = ProcessHandle.current().info().command().orElse("java");

        stdioServerSourceFile = tempDir.resolve("StdioTestServer.java");
        String javaCode = """
                import java.io.BufferedReader;
                import java.io.InputStreamReader;
                
                public class StdioTestServer {
                    public static void main(String[] args) throws Exception {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            if (line.contains("tools/list")) {
                                String id = extractId(line);
                                System.out.println("{\\"jsonrpc\\":\\"2.0\\",\\"id\\":" + id + ",\\"result\\":{\\"tools\\":[{\\"name\\":\\"stdio_ping\\",\\"description\\":\\"Ping from Stdio\\",\\"inputSchema\\":{\\"type\\":\\"object\\"}}]}}");
                                System.out.flush();
                            } else if (line.contains("tools/call")) {
                                String id = extractId(line);
                                System.out.println("{\\"jsonrpc\\":\\"2.0\\",\\"id\\":" + id + ",\\"result\\":{\\"stdioOutput\\":\\"ok\\"}}");
                                System.out.flush();
                            }
                        }
                    }
                    private static String extractId(String json) {
                        int idx = json.indexOf("\\"id\\":");
                        if (idx < 0) return "1";
                        int start = idx + 5;
                        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
                        int end = start;
                        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
                        return json.substring(start, end);
                    }
                }
                """;
        Files.writeString(stdioServerSourceFile, javaCode, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        mockHttpServer.shutdown();
        try {
            if (stdioServerSourceFile != null) {
                Files.deleteIfExists(stdioServerSourceFile);
            }
            if (tempDir != null) {
                Files.deleteIfExists(tempDir);
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("全协议混合聚合测试：tools/list 同时聚合 HTTP 与 STDIO 路由工具")
    void testMultiProtocolToolsListAggregation() {
        routeManager.clear();
        upstreamClientPool.closeAll();

        // 1. 注册 HTTP 路由
        String httpUrl = mockHttpServer.url("/mcp").toString();
        RouteDefinition httpRoute = new RouteDefinition("http-svc", UpstreamProtocol.HTTP, "web_", httpUrl);
        routeManager.registerRoute(httpRoute);

        // 2. 注册 STDIO 路由
        RouteDefinition stdioRoute = new RouteDefinition();
        stdioRoute.setId("stdio-svc");
        stdioRoute.setProtocol(UpstreamProtocol.STDIO);
        stdioRoute.setPrefix("local_");
        StdioCommandConfig cmd = new StdioCommandConfig();
        cmd.setCommand(javaBin);
        cmd.setArgs(List.of(stdioServerSourceFile.toAbsolutePath().toString()));
        cmd.setWorkingDirectory(tempDir.toAbsolutePath().toString());
        stdioRoute.setStdioCommand(cmd);
        routeManager.registerRoute(stdioRoute);

        // Mock HTTP 响应
        String httpToolsList = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "tools": [
                      {
                        "name": "http_ping",
                        "description": "Ping from HTTP",
                        "inputSchema": { "type": "object" }
                      }
                    ]
                  }
                }
                """;
        mockHttpServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(httpToolsList));

        // 请求网关聚合 tools/list
        JsonRpcRequest listReq = new JsonRpcRequest(777, "tools/list", Map.of());

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(listReq)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(777)
                .jsonPath("$.result.tools").isArray()
                .jsonPath("$.result.tools.length()").isEqualTo(2);

        // 3. 测试向 STDIO 路由发起 tools/call
        JsonRpcRequest callReq = new JsonRpcRequest(888, "tools/call", Map.of(
                "name", "local_stdio_ping",
                "arguments", Map.of()
        ));

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(callReq)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo(888)
                .jsonPath("$.result.isError").isEqualTo(false)
                .jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("stdioOutput"));

        assertThat(upstreamClientPool.size()).isGreaterThanOrEqualTo(1);
    }
}

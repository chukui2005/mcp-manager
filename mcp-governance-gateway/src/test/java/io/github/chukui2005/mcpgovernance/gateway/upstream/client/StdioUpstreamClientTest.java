package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.StdioCommandConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StdioUpstreamClient 单元与集成测试。
 * 使用跨平台通用的 Java 子进程作为 Mock STDIO MCP Server，
 * 验证基于 stdin/stdout 的 JSON-RPC 2.0 异步消息交换、敏感环境变量脱敏、心跳探测与优雅销毁。
 *
 * @author chukui2005
 * @since 0.1.0
 */
class StdioUpstreamClientTest {

    private static Path tempDir;
    private static String javaBin;
    private static Path serverSourceFile;

    @BeforeAll
    static void setUpAll() throws IOException {
        tempDir = Files.createTempDirectory("mcp-stdio-test-");
        javaBin = ProcessHandle.current().info().command().orElse("java");

        // 编写一个极轻量纯 Java 伪 MCP 子进程（通过标准输入输出读取和响应 JSON-RPC 2.0）
        serverSourceFile = tempDir.resolve("MockMcpServer.java");
        String javaCode = """
                import java.io.BufferedReader;
                import java.io.InputStreamReader;
                
                public class MockMcpServer {
                    public static void main(String[] args) throws Exception {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            
                            // 诊断日志写入 stderr，验证 stderr 异步消费
                            System.err.println("Mock received: " + line);
                            
                            if (line.contains("tools/list")) {
                                // 提取 id
                                String id = extractId(line);
                                System.out.println("{\\"jsonrpc\\":\\"2.0\\",\\"id\\":" + id + ",\\"result\\":{\\"tools\\":[{\\"name\\":\\"local_calc\\",\\"description\\":\\"Calculate locally\\",\\"inputSchema\\":{\\"type\\":\\"object\\"}}]}}");
                                System.out.flush();
                            } else if (line.contains("tools/call")) {
                                String id = extractId(line);
                                System.out.println("{\\"jsonrpc\\":\\"2.0\\",\\"id\\":" + id + ",\\"result\\":{\\"calcResult\\":42,\\"status\\":\\"success\\"}}");
                                System.out.flush();
                            } else if (line.contains("ping")) {
                                String id = extractId(line);
                                System.out.println("{\\"jsonrpc\\":\\"2.0\\",\\"id\\":" + id + ",\\"result\\":{}}");
                                System.out.flush();
                            }
                        }
                    }
                    
                    private static String extractId(String json) {
                        int idx = json.indexOf("\\"id\\":");
                        if (idx < 0) return "1";
                        int start = idx + 5;
                        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
                            start++;
                        }
                        int end = start;
                        while (end < json.length() && Character.isDigit(json.charAt(end))) {
                            end++;
                        }
                        return json.substring(start, end);
                    }
                }
                """;
        Files.writeString(serverSourceFile, javaCode, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDownAll() {
        try {
            if (serverSourceFile != null) {
                Files.deleteIfExists(serverSourceFile);
            }
            if (tempDir != null) {
                Files.deleteIfExists(tempDir);
            }
        } catch (Exception ignored) {
        }
    }

    private RouteDefinition createStdioRoute(String routeId) {
        RouteDefinition route = new RouteDefinition();
        route.setId(routeId);
        route.setProtocol(UpstreamProtocol.STDIO);
        route.setPrefix("local_");

        StdioCommandConfig cmd = new StdioCommandConfig();
        cmd.setCommand(javaBin);
        // Java 11+ 支持单文件源代码直接执行: java MockMcpServer.java
        cmd.setArgs(List.of(serverSourceFile.toAbsolutePath().toString()));
        cmd.setWorkingDirectory(tempDir.toAbsolutePath().toString());
        route.setStdioCommand(cmd);

        return route;
    }

    @Test
    @DisplayName("测试环境变量敏感信息脱敏过滤器")
    void testSanitizeEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("PATH", "/usr/bin:/bin");
        env.put("SYSTEMROOT", "C:\\Windows");
        env.put("AWS_SECRET_ACCESS_KEY", "super-secret");
        env.put("GITHUB_TOKEN", "ghp_xxxxxx");
        env.put("DB_PASSWORD", "123456");
        env.put("AUTH_CREDENTIALS", "cred");

        StdioUpstreamClient.sanitizeEnvironment(env);

        assertThat(env).containsKeys("PATH", "SYSTEMROOT");
        assertThat(env).doesNotContainKeys(
                "AWS_SECRET_ACCESS_KEY",
                "GITHUB_TOKEN",
                "DB_PASSWORD",
                "AUTH_CREDENTIALS"
        );
    }

    @Test
    @DisplayName("测试 STDIO 子进程通信：tools/list 与 tools/call")
    void testStdioToolsListAndCall() {
        RouteDefinition route = createStdioRoute("stdio-calc");
        try (StdioUpstreamClient client = new StdioUpstreamClient(route, null, 10000)) {
            assertThat(client.isAlive()).isTrue();
            assertThat(client.getRouteId()).isEqualTo("stdio-calc");

            // 1. 测试 ping
            Boolean pingOk = client.ping().block();
            assertThat(pingOk).isTrue();

            // 2. 测试 tools/list
            List<GovernedTool> tools = client.listTools().block();
            assertThat(tools).isNotNull().hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("local_calc");
            assertThat(tools.get(0).description()).isEqualTo("Calculate locally");

            // 3. 测试 tools/call
            McpInvocationResult result = client.callTool("local_calc", Map.of("a", 10, "b", 32)).block();
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.result()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> resMap = (Map<String, Object>) result.result();
            assertThat(resMap.get("calcResult")).isEqualTo(42);
        }
    }

    @Test
    @DisplayName("测试异常场景：空命令或非法工作目录应快速失败")
    void testInvalidConfiguration() {
        RouteDefinition route = new RouteDefinition();
        route.setId("bad-stdio");
        route.setProtocol(UpstreamProtocol.STDIO);

        // 未配置 stdioCommand
        assertThatThrownBy(() -> new StdioUpstreamClient(route, null, 5000))
                .isInstanceOf(IllegalArgumentException.class);

        // 配置不存在的工作目录
        StdioCommandConfig cmd = new StdioCommandConfig();
        cmd.setCommand(javaBin);
        cmd.setWorkingDirectory("/non-existent-dir-for-mcp-test-404");
        route.setStdioCommand(cmd);

        assertThatThrownBy(() -> new StdioUpstreamClient(route, null, 5000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("测试 ManagedStdioProcessPool 生命周期托管与销毁")
    void testManagedStdioProcessPool() {
        ManagedStdioProcessPool pool = new ManagedStdioProcessPool();
        RouteDefinition route = createStdioRoute("stdio-pool-test");
        StdioUpstreamClient client = new StdioUpstreamClient(route, null, 5000);

        pool.register(client);
        assertThat(pool.size()).isEqualTo(1);
        assertThat(pool.getClient("stdio-pool-test")).isSameAs(client);

        // 单独逐出
        pool.evict("stdio-pool-test");
        assertThat(pool.size()).isEqualTo(0);
        assertThat(client.isAlive()).isFalse();

        // 重新拉起并注册后测试 pool.destroy()
        StdioUpstreamClient client2 = new StdioUpstreamClient(route, null, 5000);
        pool.register(client2);
        assertThat(pool.size()).isEqualTo(1);

        pool.destroy();
        assertThat(pool.size()).isEqualTo(0);
        assertThat(client2.isAlive()).isFalse();
    }
}

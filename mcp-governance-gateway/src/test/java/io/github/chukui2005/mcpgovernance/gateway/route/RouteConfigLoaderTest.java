package io.github.chukui2005.mcpgovernance.gateway.route;

import io.github.chukui2005.mcpgovernance.gateway.config.GatewayRoutesConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RouteConfigLoader 动态路由加载器单元测试。
 * 验证 JSON 字符串解析、流读取、本地文件读取与应用到 RouteManager 的全流程。
 *
 * @author chukui2005
 */
class RouteConfigLoaderTest {

    private RouteConfigLoader loader;
    private RouteManager routeManager;

    private static final String VALID_JSON = """
            {
              "gateway": {
                "host": "127.0.0.1",
                "port": 8080,
                "corsAllowedOrigins": ["http://localhost:3000"],
                "requestTimeoutMs": 15000
              },
              "upstreams": [
                {
                  "id": "k8s-server",
                  "protocol": "http",
                  "prefix": "k8s_",
                  "uri": "http://127.0.0.1:9091/mcp",
                  "governance": {
                    "aclMode": "WHITELIST",
                    "allowedTools": ["get_pods", "list_*"],
                    "deniedTools": ["delete_*"]
                  }
                },
                {
                  "id": "db-server",
                  "protocol": "stdio",
                  "prefix": "db:",
                  "stdioCommand": {
                    "command": "db-mcp-cli",
                    "args": ["--read-only"]
                  }
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        loader = new RouteConfigLoader();
        routeManager = new RouteManager();
    }

    @Test
    @DisplayName("测试正确解析契约 JSON 配置")
    void testParseValidJson() {
        GatewayRoutesConfig config = loader.parseConfig(VALID_JSON);

        assertThat(config).isNotNull();
        assertThat(config.getGateway()).isNotNull();
        assertThat(config.getGateway().getHost()).isEqualTo("127.0.0.1");
        assertThat(config.getGateway().getPort()).isEqualTo(8080);
        assertThat(config.getGateway().getCorsAllowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(config.getGateway().getRequestTimeoutMs()).isEqualTo(15000);

        List<RouteDefinition> upstreams = config.getUpstreams();
        assertThat(upstreams).hasSize(2);

        RouteDefinition k8s = upstreams.get(0);
        assertThat(k8s.getId()).isEqualTo("k8s-server");
        assertThat(k8s.getProtocol()).isEqualTo(UpstreamProtocol.HTTP);
        assertThat(k8s.getPrefix()).isEqualTo("k8s_");
        assertThat(k8s.getGovernance()).isNotNull();
        assertThat(k8s.getGovernance().getAclMode()).isEqualTo("WHITELIST");
        assertThat(k8s.getGovernance().getAllowedTools()).containsExactly("get_pods", "list_*");
        assertThat(k8s.getGovernance().getDeniedTools()).containsExactly("delete_*");

        RouteDefinition db = upstreams.get(1);
        assertThat(db.getId()).isEqualTo("db-server");
        assertThat(db.getProtocol()).isEqualTo(UpstreamProtocol.STDIO);
        assertThat(db.getPrefix()).isEqualTo("db:");
        assertThat(db.getStdioCommand()).isNotNull();
        assertThat(db.getStdioCommand().getCommand()).isEqualTo("db-mcp-cli");
        assertThat(db.getStdioCommand().getArgs()).containsExactly("--read-only");
    }

    @Test
    @DisplayName("测试从 InputStream 读取并解析")
    void testLoadFromStream() throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(VALID_JSON.getBytes(StandardCharsets.UTF_8));
        GatewayRoutesConfig config = loader.loadFromStream(bais);

        assertThat(config).isNotNull();
        assertThat(config.getUpstreams()).hasSize(2);
    }

    @Test
    @DisplayName("测试从物理文件加载并热更新到 RouteManager")
    void testLoadFromFileAndApply() throws IOException {
        File tempFile = File.createTempFile("routes-test-", ".json");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write(VALID_JSON);
        }

        GatewayRoutesConfig config = loader.loadFromFile(tempFile);
        assertThat(config.getUpstreams()).hasSize(2);

        // 应用到 RouteManager
        routeManager.registerRoute(new RouteDefinition("old-route", UpstreamProtocol.HTTP, "old:", "http://old"));
        assertThat(routeManager.size()).isEqualTo(1);

        loader.loadAndApply("file:" + tempFile.getAbsolutePath(), routeManager, true);

        assertThat(routeManager.size()).isEqualTo(2);
        assertThat(routeManager.getRouteById("k8s-server")).isPresent();
        assertThat(routeManager.getRouteById("db-server")).isPresent();
        assertThat(routeManager.getRouteById("old-route")).isEmpty();
    }

    @Test
    @DisplayName("测试非法 JSON 或缺少必要字段时抛出异常")
    void testInvalidConfigThrows() {
        assertThatThrownBy(() -> loader.parseConfig(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路由配置 JSON 内容不能为空");

        String missingPrefixJson = """
                {
                  "gateway": { "host": "127.0.0.1", "port": 8080 },
                  "upstreams": [
                    { "id": "test-1", "protocol": "http" }
                  ]
                }
                """;
        assertThatThrownBy(() -> loader.parseConfig(missingPrefixJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须声明 prefix");

        String missingProtocolJson = """
                {
                  "gateway": { "host": "127.0.0.1", "port": 8080 },
                  "upstreams": [
                    { "id": "test-2", "prefix": "test:" }
                  ]
                }
                """;
        assertThatThrownBy(() -> loader.parseConfig(missingProtocolJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须声明 protocol");
    }
}

package io.github.chukui2005.mcpgovernance.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayServerProperties 契约与属性绑定测试。
 * 验证网关默认安全基线（默认必须绑定 127.0.0.1）与自定义属性绑定能力。
 *
 * @author chukui2005
 */
class GatewayServerPropertiesTest {

    @Test
    @DisplayName("验证默认属性安全基线 (127.0.0.1 强制默认与端口 8080)")
    void testDefaultSecurityBaseline() {
        GatewayServerProperties properties = new GatewayServerProperties();

        assertThat(properties.getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isEqualTo(8080);
        assertThat(properties.getRequestTimeoutMs()).isEqualTo(30000);
        assertThat(properties.getUpstreams()).isEmpty();
        assertThat(properties.getCorsAllowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("验证 Spring 配置属性绑定与下游路由解析")
    void testConfigurationBinding() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();

        Map<String, Object> map = Map.of(
                "mcp.governance.gateway.host", "127.0.0.1",
                "mcp.governance.gateway.port", 8888,
                "mcp.governance.gateway.request-timeout-ms", 15000,
                "mcp.governance.gateway.upstreams[0].id", "k8s-server",
                "mcp.governance.gateway.upstreams[0].protocol", "http",
                "mcp.governance.gateway.upstreams[0].prefix", "k8s_",
                "mcp.governance.gateway.upstreams[0].uri", "http://127.0.0.1:9090/mcp"
        );
        propertySources.addFirst(new MapPropertySource("testProperties", map));

        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        GatewayServerProperties properties = binder.bind("mcp.governance.gateway", GatewayServerProperties.class).get();

        assertThat(properties.getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isEqualTo(8888);
        assertThat(properties.getRequestTimeoutMs()).isEqualTo(15000);
        assertThat(properties.getUpstreams()).hasSize(1);

        RouteDefinition route = properties.getUpstreams().get(0);
        assertThat(route.getId()).isEqualTo("k8s-server");
        assertThat(route.getProtocol()).isEqualTo(UpstreamProtocol.HTTP);
        assertThat(route.getPrefix()).isEqualTo("k8s_");
        assertThat(route.getUri()).isEqualTo("http://127.0.0.1:9090/mcp");
    }
}

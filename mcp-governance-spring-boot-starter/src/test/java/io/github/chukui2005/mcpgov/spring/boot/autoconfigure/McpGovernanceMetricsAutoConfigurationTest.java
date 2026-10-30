package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.MetricsMcpInterceptor;
import io.github.chukui2005.mcpgov.spring.boot.actuator.McpGovernanceEndpoint;
import io.github.chukui2005.mcpgov.spring.boot.metrics.MicrometerMcpMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行时治理指标与 Actuator 自动装配测试。
 */
class McpGovernanceMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpGovernanceAutoConfiguration.class));

    @Test
    @DisplayName("测试当没有 MeterRegistry 时，优雅降级为 InMemoryMcpMetricsCollector 并装配 Endpoint")
    void testFallbackToInMemoryWithoutMeterRegistry() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpMetricsCollector.class);
            assertThat(context).hasSingleBean(MetricsMcpInterceptor.class);
            assertThat(context).hasSingleBean(McpGovernanceEndpoint.class);

            McpMetricsCollector collector = context.getBean(McpMetricsCollector.class);
            assertThat(collector).isInstanceOf(InMemoryMcpMetricsCollector.class);
        });
    }

    @Test
    @DisplayName("测试当存在 MeterRegistry 时，自动装配 MicrometerMcpMetricsCollector")
    void testAutoConfiguredWithMeterRegistry() {
        contextRunner.withUserConfiguration(MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpMetricsCollector.class);
                    McpMetricsCollector collector = context.getBean(McpMetricsCollector.class);
                    assertThat(collector).isInstanceOf(MicrometerMcpMetricsCollector.class);
                });
    }

    @Test
    @DisplayName("测试当配置 mcp.governance.metrics.enabled=false 时，不注入指标收集器与拦截器")
    void testMetricsDisabled() {
        contextRunner.withPropertyValues("mcp.governance.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(McpMetricsCollector.class);
                    assertThat(context).doesNotHaveBean(MetricsMcpInterceptor.class);
                    assertThat(context).doesNotHaveBean(McpGovernanceEndpoint.class);
                });
    }

    @Test
    @DisplayName("测试当配置 mcp.governance.metrics.export-to-actuator=false 时，不暴露 Actuator 端点")
    void testExportToActuatorDisabled() {
        contextRunner.withPropertyValues("mcp.governance.metrics.export-to-actuator=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpMetricsCollector.class);
                    assertThat(context).hasSingleBean(MetricsMcpInterceptor.class);
                    assertThat(context).doesNotHaveBean(McpGovernanceEndpoint.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}

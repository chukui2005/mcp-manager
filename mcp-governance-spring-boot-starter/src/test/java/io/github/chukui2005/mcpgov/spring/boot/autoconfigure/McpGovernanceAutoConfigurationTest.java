package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.spring.boot.interceptor.LoggingMcpInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starter 自动装配行为与条件装配切面测试。
 */
class McpGovernanceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpGovernanceAutoConfiguration.class));

    @Test
    @DisplayName("默认配置下，自动装配 McpGovernanceProperties、LoggingMcpInterceptor 与 McpInterceptorChain")
    void testDefaultAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpGovernanceProperties.class);
            assertThat(context).hasSingleBean(LoggingMcpInterceptor.class);
            assertThat(context).hasSingleBean(McpInterceptorChain.class);

            McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
            assertThat(chain.getInterceptors()).hasSize(1);
            assertThat(chain.getInterceptors().get(0)).isInstanceOf(LoggingMcpInterceptor.class);
        });
    }

    @Test
    @DisplayName("当 mcp.governance.enabled=false 时，没有任何治理 Bean 被注入")
    void testDisabledAutoConfiguration() {
        contextRunner.withPropertyValues("mcp.governance.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(McpGovernanceProperties.class);
                    assertThat(context).doesNotHaveBean(LoggingMcpInterceptor.class);
                    assertThat(context).doesNotHaveBean(McpInterceptorChain.class);
                });
    }

    @Test
    @DisplayName("当 mcp.governance.audit.enabled=false 时，LoggingMcpInterceptor 不装配，但链仍正常生成")
    void testAuditDisabledConfiguration() {
        contextRunner.withPropertyValues("mcp.governance.audit.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpGovernanceProperties.class);
                    assertThat(context).doesNotHaveBean(LoggingMcpInterceptor.class);
                    assertThat(context).hasSingleBean(McpInterceptorChain.class);

                    McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
                    assertThat(chain.getInterceptors()).isEmpty();
                });
    }

    @Test
    @DisplayName("用户自定义 McpInterceptor 能够被自动收集并注入链中")
    void testCustomInterceptorInjection() {
        contextRunner.withUserConfiguration(CustomInterceptorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpInterceptorChain.class);
                    McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);

                    // 默认包含 CustomInterceptor 与 LoggingMcpInterceptor
                    assertThat(chain.getInterceptors()).hasSize(2);
                    assertThat(chain.getInterceptors().get(0)).isInstanceOf(CustomTestInterceptor.class);
                    assertThat(chain.getInterceptors().get(1)).isInstanceOf(LoggingMcpInterceptor.class);
                });
    }

    @Test
    @DisplayName("用户显式定义 McpInterceptorChain 时，遵循 ConditionalOnMissingBean，不注入默认链")
    void testCustomChainOverride() {
        contextRunner.withUserConfiguration(CustomChainConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpInterceptorChain.class);
                    McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
                    assertThat(chain).isSameAs(CustomChainConfig.CUSTOM_CHAIN_INSTANCE);
                });
    }

    @Test
    @DisplayName("测试 LoggingMcpInterceptor 的敏感参数脱敏功能")
    void testLoggingInterceptorMasking() {
        McpGovernanceProperties props = new McpGovernanceProperties();
        LoggingMcpInterceptor interceptor = new LoggingMcpInterceptor(props);

        McpInvocationContext context = new McpInvocationContext(
                "database_query",
                Map.of(
                        "username", "admin",
                        "password", "p@ssword123",
                        "api_key", "sec-xyz-888",
                        "details", Map.of("secretToken", "bearer-abc")
                ),
                Map.of("clientIp", "127.0.0.1"),
                Instant.now()
        );

        McpInvocationContext maskedContext = interceptor.preHandle(context);

        assertThat(maskedContext.arguments().get("username")).isEqualTo("admin");
        assertThat(maskedContext.arguments().get("password")).isEqualTo("******");
        assertThat(maskedContext.arguments().get("api_key")).isEqualTo("******");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) maskedContext.arguments().get("details");
        assertThat(nested.get("secretToken")).isEqualTo("******");
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomInterceptorConfig {
        @Bean
        public McpInterceptor customInterceptor() {
            return new CustomTestInterceptor();
        }
    }

    static class CustomTestInterceptor implements McpInterceptor {
        @Override
        public int getOrder() {
            return 10;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomChainConfig {
        static final McpInterceptorChain CUSTOM_CHAIN_INSTANCE = new McpInterceptorChain(Collections.emptyList());

        @Bean
        public McpInterceptorChain mcpInterceptorChain() {
            return CUSTOM_CHAIN_INSTANCE;
        }
    }
}

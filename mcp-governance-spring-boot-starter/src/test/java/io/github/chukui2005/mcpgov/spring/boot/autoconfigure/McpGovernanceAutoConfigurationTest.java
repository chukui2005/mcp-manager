package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.core.runtime.policy.AclMode;
import io.github.chukui2005.mcpgov.core.runtime.policy.ParameterValidationMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclPolicy;
import io.github.chukui2005.mcpgov.spring.boot.adapter.McpGovernanceAdapter;
import io.github.chukui2005.mcpgov.spring.boot.interceptor.LoggingMcpInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Starter 自动装配行为与条件装配切面测试。
 * 覆盖 ACL、参数校验、默认链路、自定义覆盖与端到端适配器调用场景。
 */
class McpGovernanceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpGovernanceAutoConfiguration.class));

    @Test
    @DisplayName("测试场景7：默认配置下，自动装配 McpGovernanceProperties、ToolAclMcpInterceptor、LoggingMcpInterceptor 与 McpInterceptorChain")
    void testDefaultAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpGovernanceProperties.class);
            assertThat(context).hasSingleBean(ToolAclMcpInterceptor.class);
            assertThat(context).hasSingleBean(LoggingMcpInterceptor.class);
            assertThat(context).hasSingleBean(McpInterceptorChain.class);
            // 校验默认未启用 validation
            assertThat(context).doesNotHaveBean(ParameterValidationMcpInterceptor.class);

            McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
            // ToolAcl (100) -> Logging (1000)
            assertThat(chain.getInterceptors()).hasSize(2);
            assertThat(chain.getInterceptors().get(0)).isInstanceOf(ToolAclMcpInterceptor.class);
            assertThat(chain.getInterceptors().get(1)).isInstanceOf(LoggingMcpInterceptor.class);
        });
    }

    @Test
    @DisplayName("测试场景7-补充：配置开启 validation 时，正确装配并注册两个拦截器入链")
    void testAclAndValidationAutoConfigured() {
        contextRunner.withPropertyValues(
                "mcp.governance.acl.default-mode=WHITELIST",
                "mcp.governance.acl.allowed-tools[0]=calculator",
                "mcp.governance.acl.allowed-tools[1]=file_*",
                "mcp.governance.validation.enabled=true",
                "mcp.governance.validation.rules[0].tool-pattern=file_*",
                "mcp.governance.validation.rules[0].parameter=path",
                "mcp.governance.validation.rules[0].required=true",
                "mcp.governance.validation.rules[0].disallowed-regex=.*(\\.\\./|~).*",
                "mcp.governance.validation.rules[0].error-message=路径遍历被禁止"
        ).run(context -> {
            assertThat(context).hasSingleBean(ToolAclMcpInterceptor.class);
            assertThat(context).hasSingleBean(ParameterValidationMcpInterceptor.class);
            assertThat(context).hasSingleBean(LoggingMcpInterceptor.class);

            McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
            // 顺序: ToolAcl(100) -> ParameterValidation(200) -> Logging(1000)
            assertThat(chain.getInterceptors()).hasSize(3);
            assertThat(chain.getInterceptors().get(0)).isInstanceOf(ToolAclMcpInterceptor.class);
            assertThat(chain.getInterceptors().get(1)).isInstanceOf(ParameterValidationMcpInterceptor.class);
            assertThat(chain.getInterceptors().get(2)).isInstanceOf(LoggingMcpInterceptor.class);

            // 验证端到端 Adapter 调用阻断
            McpGovernanceAdapter adapter = new McpGovernanceAdapter(chain);

            // 未在白名单的 tool 被 ACL (Order 100) 拦截
            assertThatThrownBy(() -> adapter.executeGoverned("unauthorized_tool", Map.of(), Map.of(), args -> "ok"))
                    .isInstanceOf(McpAccessDeniedException.class)
                    .hasMessageContaining("OWASP MCP02");

            // 在白名单中的 file_read，但参数缺少 path，被 Validation (Order 200) 拦截
            assertThatThrownBy(() -> adapter.executeGoverned("file_read", Map.of(), Map.of(), args -> "ok"))
                    .isInstanceOf(McpGovernanceException.class)
                    .hasMessageContaining("路径遍历被禁止");

            // 在白名单中，但入参包含路径遍历，被 Validation 拦截
            assertThatThrownBy(() -> adapter.executeGoverned("file_read", Map.of("path", "../etc/passwd"), Map.of(), args -> "ok"))
                    .isInstanceOf(McpGovernanceException.class)
                    .hasMessageContaining("路径遍历被禁止");

            // 合规调用，成功执行
            McpInvocationResult result = adapter.executeGoverned("file_read", Map.of("path", "/data/safe.txt"), Map.of(), args -> "content");
            assertThat(result.success()).isTrue();
            assertThat(result.result()).isEqualTo("content");
        });
    }

    @Test
    @DisplayName("测试场景8：当声明自定义 ToolAclMcpInterceptor 时，ConditionalOnMissingBean 生效，不注入默认 Bean")
    void testCustomAclInterceptorOverride() {
        contextRunner.withUserConfiguration(CustomAclConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ToolAclMcpInterceptor.class);
                    ToolAclMcpInterceptor interceptor = context.getBean(ToolAclMcpInterceptor.class);
                    assertThat(interceptor).isSameAs(CustomAclConfig.CUSTOM_ACL_INSTANCE);
                });
    }

    @Test
    @DisplayName("当 mcp.governance.enabled=false 时，没有任何治理 Bean 被注入")
    void testDisabledAutoConfiguration() {
        contextRunner.withPropertyValues("mcp.governance.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(McpGovernanceProperties.class);
                    assertThat(context).doesNotHaveBean(ToolAclMcpInterceptor.class);
                    assertThat(context).doesNotHaveBean(LoggingMcpInterceptor.class);
                    assertThat(context).doesNotHaveBean(McpInterceptorChain.class);
                });
    }

    @Test
    @DisplayName("当 mcp.governance.acl.enabled=false 时，ToolAclMcpInterceptor 不装配")
    void testAclDisabledConfiguration() {
        contextRunner.withPropertyValues("mcp.governance.acl.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpGovernanceProperties.class);
                    assertThat(context).doesNotHaveBean(ToolAclMcpInterceptor.class);
                    assertThat(context).hasSingleBean(LoggingMcpInterceptor.class);
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
                });
    }

    @Test
    @DisplayName("用户自定义 McpInterceptor 能够被自动收集并注入链中")
    void testCustomInterceptorInjection() {
        contextRunner.withUserConfiguration(CustomInterceptorConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(McpInterceptorChain.class);
                    McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);

                    // 默认包含 CustomInterceptor(10), ToolAcl(100), Logging(1000)
                    assertThat(chain.getInterceptors()).hasSize(3);
                    assertThat(chain.getInterceptors().get(0)).isInstanceOf(CustomTestInterceptor.class);
                    assertThat(chain.getInterceptors().get(1)).isInstanceOf(ToolAclMcpInterceptor.class);
                    assertThat(chain.getInterceptors().get(2)).isInstanceOf(LoggingMcpInterceptor.class);
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
    static class CustomAclConfig {
        static final ToolAclMcpInterceptor CUSTOM_ACL_INSTANCE = new ToolAclMcpInterceptor(
                new ToolAclPolicy(true, AclMode.WHITELIST, List.of("custom_tool"), List.of())
        );

        @Bean
        public ToolAclMcpInterceptor toolAclMcpInterceptor() {
            return CUSTOM_ACL_INSTANCE;
        }
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

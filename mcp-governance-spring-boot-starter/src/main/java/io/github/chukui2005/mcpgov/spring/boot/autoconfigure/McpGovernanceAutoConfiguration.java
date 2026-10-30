package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.MetricsMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ParameterValidationMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ParameterValidationRule;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclPolicy;
import io.github.chukui2005.mcpgov.spring.boot.actuator.McpGovernanceEndpoint;
import io.github.chukui2005.mcpgov.spring.boot.interceptor.LoggingMcpInterceptor;
import io.github.chukui2005.mcpgov.spring.boot.metrics.MicrometerMcpMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * MCP 运行时治理自动装配入口类。
 * 仅在存在 {@link McpInterceptor} 类且 {@code mcp.governance.enabled} 开启时装配。
 */
@AutoConfiguration
@ConditionalOnClass(McpInterceptor.class)
@EnableConfigurationProperties(McpGovernanceProperties.class)
@ConditionalOnProperty(prefix = "mcp.governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpGovernanceAutoConfiguration {

    /**
     * 默认无参构造函数。
     */
    public McpGovernanceAutoConfiguration() {
    }

    /**
     * 当开启 Tool ACL 且容器中不存在自定义 ToolAclMcpInterceptor 时，注册默认的 ACL 拦截器。
     *
     * @param properties 治理配置属性
     * @return 默认 Tool ACL 拦截器
     */
    @Bean
    @ConditionalOnMissingBean(ToolAclMcpInterceptor.class)
    @ConditionalOnProperty(prefix = "mcp.governance.acl", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ToolAclMcpInterceptor toolAclMcpInterceptor(McpGovernanceProperties properties) {
        McpGovernanceProperties.AclProperties acl = properties.getAcl();
        ToolAclPolicy policy = new ToolAclPolicy(
            acl.isEnabled(),
            acl.getDefaultMode(),
            acl.getAllowedTools(),
            acl.getBlockedTools()
        );
        return new ToolAclMcpInterceptor(policy);
    }

    /**
     * 当开启参数校验且容器中不存在自定义 ParameterValidationMcpInterceptor 时，注册默认的参数校验拦截器。
     *
     * @param properties 治理配置属性
     * @return 默认参数校验拦截器
     */
    @Bean
    @ConditionalOnMissingBean(ParameterValidationMcpInterceptor.class)
    @ConditionalOnProperty(prefix = "mcp.governance.validation", name = "enabled", havingValue = "true")
    public ParameterValidationMcpInterceptor parameterValidationMcpInterceptor(McpGovernanceProperties properties) {
        List<ParameterValidationRule> rules = properties.getValidation().getRules().stream()
            .map(r -> new ParameterValidationRule(
                r.getToolPattern(),
                r.getParameter(),
                r.isRequired(),
                r.getDisallowedRegex(),
                r.getRequiredRegex(),
                r.getErrorMessage()
            ))
            .toList();
        return new ParameterValidationMcpInterceptor(rules);
    }

    /**
     * 当开启审计且容器中不存在自定义 LoggingMcpInterceptor 时，注册默认的脱敏审计拦截器。
     *
     * @param properties 治理配置属性
     * @return 默认日志拦截器
     */
    @Bean
    @ConditionalOnMissingBean(LoggingMcpInterceptor.class)
    @ConditionalOnProperty(prefix = "mcp.governance.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingMcpInterceptor loggingMcpInterceptor(McpGovernanceProperties properties) {
        return new LoggingMcpInterceptor(properties);
    }

    /**
     * 当开启指标遥测且容器中不存在自定义 McpMetricsCollector 时，注册默认的度量收集器。
     * 若环境中存在 MeterRegistry 且启用了 micrometer-enabled，则桥接 Micrometer；否则降级为 InMemory 收集器。
     *
     * @param properties 治理配置属性
     * @param meterRegistryProvider Micrometer 注册表提供者
     * @return 运行时指标收集器
     */
    @Bean
    @ConditionalOnMissingBean(McpMetricsCollector.class)
    @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpMetricsCollector mcpMetricsCollector(
            McpGovernanceProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = null;
        try {
            meterRegistry = meterRegistryProvider.getIfAvailable();
        } catch (NoClassDefFoundError ignored) {
            // 环境中未引入 micrometer-core，安全忽略
        }

        boolean useMicrometer = properties.getMetrics().isMicrometerEnabled() && meterRegistry != null;
        if (useMicrometer) {
            return new MicrometerMcpMetricsCollector(meterRegistry);
        }
        return new InMemoryMcpMetricsCollector();
    }

    /**
     * 当开启指标遥测且容器中不存在自定义 MetricsMcpInterceptor 时，注册默认的指标拦截器。
     *
     * @param collector 运行时指标收集器
     * @return 默认指标拦截器
     */
    @Bean
    @ConditionalOnMissingBean(MetricsMcpInterceptor.class)
    @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MetricsMcpInterceptor metricsMcpInterceptor(McpMetricsCollector collector) {
        return new MetricsMcpInterceptor(collector);
    }

    /**
     * 当环境中存在 Actuator Endpoint 且开启配置与指标收集器时，注册 /actuator/mcpGovernance 端点。
     *
     * @param collector 运行时指标收集器
     * @return Actuator 监控端点
     */
    @Bean
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnBean(McpMetricsCollector.class)
    @ConditionalOnMissingBean(McpGovernanceEndpoint.class)
    @ConditionalOnProperty(prefix = "mcp.governance.metrics", name = "export-to-actuator", havingValue = "true", matchIfMissing = true)
    public McpGovernanceEndpoint mcpGovernanceEndpoint(McpMetricsCollector collector) {
        return new McpGovernanceEndpoint(collector);
    }

    /**
     * 收集容器中所有已声明的 {@link McpInterceptor}，并装配全局 {@link McpInterceptorChain}。
     * 允许宿主应用完全替换自定义链 Bean。
     *
     * @param interceptorsProvider 容器中的拦截器提供者
     * @return 全局责任链执行器
     */
    @Bean
    @ConditionalOnMissingBean(McpInterceptorChain.class)
    public McpInterceptorChain mcpInterceptorChain(ObjectProvider<List<McpInterceptor>> interceptorsProvider) {
        List<McpInterceptor> interceptors = interceptorsProvider.getIfAvailable();
        return new McpInterceptorChain(interceptors);
    }
}

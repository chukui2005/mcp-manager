package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.spring.boot.interceptor.LoggingMcpInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

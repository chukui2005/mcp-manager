package io.github.chukui2005.mcpgovernance.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 网关服务基础 WebFlux 安全与跨域配置。
 * 遵循 OWASP 安全基线，按配置严格约束跨域 Origin 访问。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Configuration
public class GatewayWebFluxConfig {

    /**
     * 默认构造函数。
     */
    public GatewayWebFluxConfig() {
    }

    /**
     * 注册响应式跨域 WebFilter。
     *
     * @param properties 网关全局配置属性
     * @return CorsWebFilter 实例
     */
    @Bean
    public CorsWebFilter corsWebFilter(GatewayServerProperties properties) {
        List<String> allowedOrigins = properties != null ? properties.getCorsAllowedOrigins() : null;
        CorsConfiguration config = new CorsConfiguration();

        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            for (String origin : allowedOrigins) {
                if (origin != null && !origin.trim().isEmpty()) {
                    config.addAllowedOrigin(origin.trim());
                }
            }
        } else {
            // 默认受限仅允许本地同源请求
            config.addAllowedOrigin("http://127.0.0.1");
            config.addAllowedOrigin("http://localhost");
        }

        config.addAllowedMethod(HttpMethod.POST);
        config.addAllowedMethod(HttpMethod.GET);
        config.addAllowedMethod(HttpMethod.OPTIONS);
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * 注册网关默认的纯内存指标收集器 Bean。
     *
     * @return McpMetricsCollector 实例
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector mcpMetricsCollector() {
        return new io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector();
    }

    /**
     * 注册网关默认的内存审计事件环形缓冲器与 SSE 广播器 Bean。
     *
     * @return InMemoryAuditRingBuffer 实例
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer inMemoryAuditRingBuffer() {
        return new io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer();
    }

    /**
     * 注册网关审计发布器 Bean，委托 InMemoryAuditRingBuffer 作为核心发布实现。
     *
     * @param ringBuffer 环形缓冲器
     * @return McpAuditPublisher 实例
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditPublisher mcpAuditPublisher(
            io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer ringBuffer) {
        return ringBuffer;
    }
}

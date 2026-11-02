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
}

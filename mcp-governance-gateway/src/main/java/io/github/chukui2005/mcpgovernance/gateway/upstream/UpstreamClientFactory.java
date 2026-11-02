package io.github.chukui2005.mcpgovernance.gateway.upstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgovernance.gateway.config.GatewayServerProperties;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.upstream.auth.TokenRefResolver;
import io.github.chukui2005.mcpgovernance.gateway.upstream.client.HttpUpstreamClient;
import io.github.chukui2005.mcpgovernance.gateway.upstream.client.SseUpstreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 下游出站客户端工厂。
 * 负责依据路由定义的协议类型（HTTP、SSE、STDIO 等）动态构建对应的 {@link UpstreamClient} 实例。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class UpstreamClientFactory {

    private static final Logger log = LoggerFactory.getLogger(UpstreamClientFactory.class);

    private final TokenRefResolver tokenRefResolver;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final GatewayServerProperties properties;

    /**
     * 构造函数注入依赖。
     *
     * @param tokenRefResolver 安全凭据解析器
     * @param webClientBuilder WebClient.Builder 实例
     * @param objectMapper     JSON 映射器
     * @param properties       网关配置属性
     */
    public UpstreamClientFactory(TokenRefResolver tokenRefResolver,
                                 WebClient.Builder webClientBuilder,
                                 ObjectMapper objectMapper,
                                 GatewayServerProperties properties) {
        this.tokenRefResolver = tokenRefResolver;
        this.webClientBuilder = webClientBuilder != null ? webClientBuilder : WebClient.builder();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.properties = properties;
    }

    /**
     * 根据路由定义创建出站客户端实例。
     *
     * @param route 路由规则定义
     * @return 构造完成的 UpstreamClient 实例
     * @throws IllegalArgumentException 当传入 route 为空或协议不受支持时抛出
     */
    public UpstreamClient createClient(RouteDefinition route) {
        if (route == null) {
            throw new IllegalArgumentException("route 不能为空");
        }

        UpstreamProtocol protocol = route.getProtocol();
        if (protocol == null) {
            throw new IllegalArgumentException("路由 [" + route.getId() + "] 未指定协议类型 (protocol)");
        }

        int defaultTimeout = properties != null ? properties.getRequestTimeoutMs() : 30000;

        switch (protocol) {
            case HTTP:
                log.info("为下游路由 [{}] 创建 HttpUpstreamClient", route.getId());
                return new HttpUpstreamClient(route, tokenRefResolver, webClientBuilder, objectMapper, defaultTimeout);
            case SSE:
                log.info("为下游路由 [{}] 创建 SseUpstreamClient", route.getId());
                return new SseUpstreamClient(route, tokenRefResolver, webClientBuilder, objectMapper, defaultTimeout);
            case STDIO:
                throw new UnsupportedOperationException("STDIO 隔离进程出站客户端暂未开放，将在后续任务中提供");
            default:
                throw new IllegalArgumentException("不支持的下游协议类型: " + protocol);
        }
    }
}

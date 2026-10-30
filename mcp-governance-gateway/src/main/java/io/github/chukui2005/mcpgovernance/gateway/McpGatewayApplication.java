package io.github.chukui2005.mcpgovernance.gateway;

import io.github.chukui2005.mcpgovernance.gateway.config.GatewayServerProperties;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MCP Governance 独立网关服务启动入口。
 * 提供响应式 JSON-RPC 路由、多协议代理与聚合治理能力。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayServerProperties.class)
public class McpGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayApplication.class);

    private final GatewayServerProperties properties;
    private final RouteManager routeManager;

    /**
     * 构造函数注入网关核心配置与路由管理器。
     *
     * @param properties   网关配置属性
     * @param routeManager 路由管理器
     */
    public McpGatewayApplication(GatewayServerProperties properties, RouteManager routeManager) {
        this.properties = properties;
        this.routeManager = routeManager;
    }

    /**
     * 容器初始化后自动加载配置文件中配置的静态路由规则。
     */
    @PostConstruct
    public void initRoutes() {
        if (properties.getUpstreams() != null && !properties.getUpstreams().isEmpty()) {
            routeManager.registerRoutes(properties.getUpstreams());
            log.info("MCP 网关从配置成功初始化加载 {} 条下游路由", properties.getUpstreams().size());
        } else {
            log.info("MCP 网关启动完成，当前无预配置静态下游路由");
        }
    }

    /**
     * 网关应用启动 main 方法。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(McpGatewayApplication.class, args);
    }
}

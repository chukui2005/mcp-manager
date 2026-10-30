package io.github.chukui2005.mcpgovernance.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 网关服务全局配置属性。
 * 映射配置前缀为 mcp.governance.gateway，严格遵循安全基线：默认强制绑定 127.0.0.1 本地回环地址。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "mcp.governance.gateway")
public class GatewayServerProperties {

    /**
     * 网关监听主机地址。安全基线约束：默认强制绑定 127.0.0.1。
     */
    private String host = "127.0.0.1";

    /**
     * 网关监听端口，默认为 8080。
     */
    private int port = 8080;

    /**
     * 允许跨域访问的 Origin 列表。
     */
    private List<String> corsAllowedOrigins = new ArrayList<>();

    /**
     * 全局请求超时时间（毫秒），默认为 30000ms。
     */
    private int requestTimeoutMs = 30000;

    /**
     * 下游代理路由规则列表。
     */
    private List<RouteDefinition> upstreams = new ArrayList<>();

    /**
     * 默认构造函数。
     */
    public GatewayServerProperties() {
    }

    /**
     * 获取网关监听主机地址。
     *
     * @return 监听主机地址
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置网关监听主机地址。
     *
     * @param host 监听主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 获取网关监听端口。
     *
     * @return 监听端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置网关监听端口。
     *
     * @param port 监听端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 获取允许跨域访问的 Origin 列表。
     *
     * @return 跨域 Origin 列表
     */
    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    /**
     * 设置允许跨域访问的 Origin 列表。
     *
     * @param corsAllowedOrigins 跨域 Origin 列表
     */
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins != null ? corsAllowedOrigins : new ArrayList<>();
    }

    /**
     * 获取全局请求超时时间（毫秒）。
     *
     * @return 超时时间毫秒数
     */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * 设置全局请求超时时间（毫秒）。
     *
     * @param requestTimeoutMs 超时时间毫秒数
     */
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /**
     * 获取下游代理路由规则列表。
     *
     * @return 路由规则列表
     */
    public List<RouteDefinition> getUpstreams() {
        return upstreams;
    }

    /**
     * 设置下游代理路由规则列表。
     *
     * @param upstreams 路由规则列表
     */
    public void setUpstreams(List<RouteDefinition> upstreams) {
        this.upstreams = upstreams != null ? upstreams : new ArrayList<>();
    }
}

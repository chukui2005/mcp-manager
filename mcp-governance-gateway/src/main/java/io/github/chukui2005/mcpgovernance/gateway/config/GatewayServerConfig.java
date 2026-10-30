package io.github.chukui2005.mcpgovernance.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 契约中的 gateway 节点配置模型。
 * 遵循安全基线：默认强制绑定 127.0.0.1 本地回环地址。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayServerConfig {

    /**
     * 网关监听主机地址。安全基线约束：默认强制绑定 127.0.0.1。
     */
    @JsonProperty("host")
    private String host = "127.0.0.1";

    /**
     * 网关监听端口，默认为 8080。
     */
    @JsonProperty("port")
    private int port = 8080;

    /**
     * 允许跨域访问的 Origin 列表。
     */
    @JsonProperty("corsAllowedOrigins")
    private List<String> corsAllowedOrigins = new ArrayList<>();

    /**
     * 全局请求超时时间（毫秒），默认为 30000ms。
     */
    @JsonProperty("requestTimeoutMs")
    private int requestTimeoutMs = 30000;

    /**
     * 默认构造函数。
     */
    public GatewayServerConfig() {
    }

    /**
     * 全参构造函数。
     *
     * @param host               主机地址
     * @param port               端口
     * @param corsAllowedOrigins 跨域列表
     * @param requestTimeoutMs   超时毫秒数
     */
    public GatewayServerConfig(String host, int port, List<String> corsAllowedOrigins, int requestTimeoutMs) {
        this.host = host != null ? host : "127.0.0.1";
        this.port = port > 0 ? port : 8080;
        this.corsAllowedOrigins = corsAllowedOrigins != null ? corsAllowedOrigins : new ArrayList<>();
        this.requestTimeoutMs = requestTimeoutMs > 0 ? requestTimeoutMs : 30000;
    }

    /**
     * 获取主机地址。
     *
     * @return 主机地址
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置主机地址。
     *
     * @param host 主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 获取端口。
     *
     * @return 端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置端口。
     *
     * @param port 端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 获取允许跨域列表。
     *
     * @return 跨域列表
     */
    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    /**
     * 设置允许跨域列表。
     *
     * @param corsAllowedOrigins 跨域列表
     */
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins != null ? corsAllowedOrigins : new ArrayList<>();
    }

    /**
     * 获取请求超时毫秒数。
     *
     * @return 超时毫秒数
     */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * 设置请求超时毫秒数。
     *
     * @param requestTimeoutMs 超时毫秒数
     */
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayServerConfig that = (GatewayServerConfig) o;
        return port == that.port &&
                requestTimeoutMs == that.requestTimeoutMs &&
                Objects.equals(host, that.host) &&
                Objects.equals(corsAllowedOrigins, that.corsAllowedOrigins);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, corsAllowedOrigins, requestTimeoutMs);
    }
}

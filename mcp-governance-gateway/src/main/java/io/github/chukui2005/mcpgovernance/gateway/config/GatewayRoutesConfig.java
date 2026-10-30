package io.github.chukui2005.mcpgovernance.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 网关配置模型，严格映射 docs/contracts/gateway-routes.schema.json 契约。
 * 包含 gateway 网关服务器配置与 upstreams 下游路由规则列表。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayRoutesConfig {

    /**
     * 网关服务器运行参数。
     */
    @JsonProperty("gateway")
    private GatewayServerConfig gateway;

    /**
     * 下游代理路由规则清单。
     */
    @JsonProperty("upstreams")
    private List<RouteDefinition> upstreams = new ArrayList<>();

    /**
     * 默认构造函数。
     */
    public GatewayRoutesConfig() {
    }

    /**
     * 全参构造函数。
     *
     * @param gateway   网关配置
     * @param upstreams 下游路由列表
     */
    public GatewayRoutesConfig(GatewayServerConfig gateway, List<RouteDefinition> upstreams) {
        this.gateway = gateway;
        this.upstreams = upstreams != null ? upstreams : new ArrayList<>();
    }

    /**
     * 获取网关配置。
     *
     * @return 网关配置
     */
    public GatewayServerConfig getGateway() {
        return gateway;
    }

    /**
     * 设置网关配置。
     *
     * @param gateway 网关配置
     */
    public void setGateway(GatewayServerConfig gateway) {
        this.gateway = gateway;
    }

    /**
     * 获取下游路由列表。
     *
     * @return 路由定义列表
     */
    public List<RouteDefinition> getUpstreams() {
        return upstreams;
    }

    /**
     * 设置下游路由列表。
     *
     * @param upstreams 路由定义列表
     */
    public void setUpstreams(List<RouteDefinition> upstreams) {
        this.upstreams = upstreams != null ? upstreams : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GatewayRoutesConfig that = (GatewayRoutesConfig) o;
        return Objects.equals(gateway, that.gateway) &&
                Objects.equals(upstreams, that.upstreams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gateway, upstreams);
    }
}

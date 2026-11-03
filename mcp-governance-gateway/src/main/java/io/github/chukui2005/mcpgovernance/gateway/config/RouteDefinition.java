package io.github.chukui2005.mcpgovernance.gateway.config;

import java.util.Objects;

/**
 * 下游 MCP Server 路由规则定义模型。
 * 对齐 gateway-routes.schema.json 中的 upstreams 元素。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class RouteDefinition {

    /**
     * 下游服务的唯一业务标识符。
     */
    private String id;

    /**
     * 下游协议类型（sse, http, stdio）。
     */
    private UpstreamProtocol protocol;

    /**
     * 命名空间前缀，用于聚合 tools/list 避免名称冲突并实现前缀分发。
     */
    private String prefix;

    /**
     * 下游远程服务地址（适用于 sse 与 http 协议）。
     */
    private String uri;

    /**
     * 本地子进程命令配置（仅当 protocol 为 stdio 时生效）。
     */
    private StdioCommandConfig stdioCommand;

    /**
     * 下游凭证配置。
     */
    private UpstreamAuthConfig auth;

    /**
     * 路由特定治理规则。
     */
    private RouteGovernanceConfig governance;

    /**
     * 是否启用该下游路由（默认为 true 启用；当置为 false 时，该路由热停用，下属工具不在 tools/list 暴露且拒绝调用）。
     */
    private volatile boolean enabled = true;

    /**
     * 默认构造函数。
     */
    public RouteDefinition() {
    }

    /**
     * 快速构建路由定义对象的便捷构造函数。
     *
     * @param id       路由标识
     * @param protocol 协议类型
     * @param prefix   前缀
     * @param uri      远程地址
     */
    public RouteDefinition(String id, UpstreamProtocol protocol, String prefix, String uri) {
        this.id = id;
        this.protocol = protocol;
        this.prefix = prefix;
        this.uri = uri;
    }

    /**
     * 获取下游服务的唯一业务标识符。
     *
     * @return 路由标识
     */
    public String getId() {
        return id;
    }

    /**
     * 设置下游服务的唯一业务标识符。
     *
     * @param id 路由标识
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取下游协议类型。
     *
     * @return 协议类型
     */
    public UpstreamProtocol getProtocol() {
        return protocol;
    }

    /**
     * 设置下游协议类型。
     *
     * @param protocol 协议类型
     */
    public void setProtocol(UpstreamProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * 获取命名空间前缀。
     *
     * @return 前缀字符串
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 设置命名空间前缀。
     *
     * @param prefix 前缀字符串
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 获取下游远程服务地址。
     *
     * @return 远程服务地址
     */
    public String getUri() {
        return uri;
    }

    /**
     * 设置下游远程服务地址。
     *
     * @param uri 远程服务地址
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * 获取本地子进程命令配置。
     *
     * @return 本地子进程命令配置
     */
    public StdioCommandConfig getStdioCommand() {
        return stdioCommand;
    }

    /**
     * 设置本地子进程命令配置。
     *
     * @param stdioCommand 本地子进程命令配置
     */
    public void setStdioCommand(StdioCommandConfig stdioCommand) {
        this.stdioCommand = stdioCommand;
    }

    /**
     * 获取下游凭证配置。
     *
     * @return 下游凭证配置
     */
    public UpstreamAuthConfig getAuth() {
        return auth;
    }

    /**
     * 设置下游凭证配置。
     *
     * @param auth 下游凭证配置
     */
    public void setAuth(UpstreamAuthConfig auth) {
        this.auth = auth;
    }

    /**
     * 获取路由特定治理规则。
     *
     * @return 治理规则
     */
    public RouteGovernanceConfig getGovernance() {
        return governance;
    }

    /**
     * 设置路由特定治理规则。
     *
     * @param governance 治理规则
     */
    public void setGovernance(RouteGovernanceConfig governance) {
        this.governance = governance;
    }

    /**
     * 获取路由是否启用。
     *
     * @return true 表示启用，false 表示停用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置路由是否启用。
     *
     * @param enabled true 表示启用，false 表示停用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteDefinition that = (RouteDefinition) o;
        return enabled == that.enabled &&
                Objects.equals(id, that.id) &&
                protocol == that.protocol &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(stdioCommand, that.stdioCommand) &&
                Objects.equals(auth, that.auth) &&
                Objects.equals(governance, that.governance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, protocol, prefix, uri, stdioCommand, auth, governance, enabled);
    }
}

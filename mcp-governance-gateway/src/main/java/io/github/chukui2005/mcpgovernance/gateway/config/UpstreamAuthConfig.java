package io.github.chukui2005.mcpgovernance.gateway.config;

import java.util.Objects;

/**
 * 下游认证与凭证配置。
 * 网关独立持有下游鉴权凭证，严禁向上游客户端透传。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class UpstreamAuthConfig {

    /**
     * 认证类型，支持 bearer, apiKey, basic。
     */
    private String type;

    /**
     * 请求头名称，默认为 Authorization。
     */
    private String headerName = "Authorization";

    /**
     * 环境变量引用名称，如 ${GITHUB_MCP_TOKEN}，禁止明文写入。
     */
    private String tokenRef;

    /**
     * 默认构造函数。
     */
    public UpstreamAuthConfig() {
    }

    /**
     * 获取认证类型。
     *
     * @return 认证类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置认证类型。
     *
     * @param type 认证类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取请求头名称。
     *
     * @return 请求头名称
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * 设置请求头名称。
     *
     * @param headerName 请求头名称
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName != null ? headerName : "Authorization";
    }

    /**
     * 获取环境变量引用。
     *
     * @return 环境变量引用表达式
     */
    public String getTokenRef() {
        return tokenRef;
    }

    /**
     * 设置环境变量引用。
     *
     * @param tokenRef 环境变量引用表达式
     */
    public void setTokenRef(String tokenRef) {
        this.tokenRef = tokenRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpstreamAuthConfig that = (UpstreamAuthConfig) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(headerName, that.headerName) &&
                Objects.equals(tokenRef, that.tokenRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, headerName, tokenRef);
    }
}

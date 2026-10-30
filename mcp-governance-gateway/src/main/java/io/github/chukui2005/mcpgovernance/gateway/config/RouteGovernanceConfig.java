package io.github.chukui2005.mcpgovernance.gateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 下游服务的路由特定治理配置。
 * 支持针对单个下游单独配置 ACL 模式、允许/拒绝工具清单。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class RouteGovernanceConfig {

    /**
     * ACL 模式：WHITELIST 或 BLACKLIST。
     */
    private String aclMode;

    /**
     * 允许调用的工具列表。
     */
    private List<String> allowedTools = new ArrayList<>();

    /**
     * 禁止调用的工具列表。
     */
    private List<String> deniedTools = new ArrayList<>();

    /**
     * 默认构造函数。
     */
    public RouteGovernanceConfig() {
    }

    /**
     * 获取 ACL 模式。
     *
     * @return ACL 模式
     */
    public String getAclMode() {
        return aclMode;
    }

    /**
     * 设置 ACL 模式。
     *
     * @param aclMode ACL 模式
     */
    public void setAclMode(String aclMode) {
        this.aclMode = aclMode;
    }

    /**
     * 获取允许调用的工具列表。
     *
     * @return 允许调用的工具列表
     */
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    /**
     * 设置允许调用的工具列表。
     *
     * @param allowedTools 允许调用的工具列表
     */
    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools != null ? allowedTools : new ArrayList<>();
    }

    /**
     * 获取禁止调用的工具列表。
     *
     * @return 禁止调用的工具列表
     */
    public List<String> getDeniedTools() {
        return deniedTools;
    }

    /**
     * 设置禁止调用的工具列表。
     *
     * @param deniedTools 禁止调用的工具列表
     */
    public void setDeniedTools(List<String> deniedTools) {
        this.deniedTools = deniedTools != null ? deniedTools : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteGovernanceConfig that = (RouteGovernanceConfig) o;
        return Objects.equals(aclMode, that.aclMode) &&
                Objects.equals(allowedTools, that.allowedTools) &&
                Objects.equals(deniedTools, that.deniedTools);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aclMode, allowedTools, deniedTools);
    }
}

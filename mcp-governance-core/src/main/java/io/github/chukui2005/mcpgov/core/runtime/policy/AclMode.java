package io.github.chukui2005.mcpgov.core.runtime.policy;

/**
 * Tool ACL 访问控制模式枚举。
 */
public enum AclMode {

    /**
     * 白名单模式：仅允许白名单内工具执行，其余默认拒绝 (Default Deny)。
     */
    WHITELIST,

    /**
     * 黑名单模式：仅阻断黑名单内工具执行，其余默认放行 (Default Allow)。
     */
    BLACKLIST
}

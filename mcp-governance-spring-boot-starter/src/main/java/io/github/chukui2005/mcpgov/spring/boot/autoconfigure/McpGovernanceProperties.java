package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 运行时治理配置属性类。
 * 映射配置前缀 {@code mcp.governance}。
 */
@ConfigurationProperties(prefix = "mcp.governance")
public class McpGovernanceProperties {

    /**
     * MCP 治理运行时拦截总开关，默认为 true。
     */
    private boolean enabled = true;

    /**
     * 是否在遭遇安全阻断或校验失败时快速失败并终止调用链，默认为 true。
     */
    private boolean failFast = true;

    /**
     * 调用审计相关配置。
     */
    private AuditProperties audit = new AuditProperties();

    /**
     * 获取治理总开关状态。
     *
     * @return 是否启用治理
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置治理总开关状态。
     *
     * @param enabled 是否启用治理
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取是否快速失败。
     *
     * @return 是否快速失败
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * 设置是否快速失败。
     *
     * @param failFast 是否快速失败
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * 获取审计相关配置。
     *
     * @return 审计配置
     */
    public AuditProperties getAudit() {
        return audit;
    }

    /**
     * 设置审计相关配置。
     *
     * @param audit 审计配置
     */
    public void setAudit(AuditProperties audit) {
        this.audit = audit;
    }

    /**
     * 调用审计配置属性。
     */
    public static class AuditProperties {

        /**
         * 是否开启调用审计日志，默认为 true。
         */
        private boolean enabled = true;

        /**
         * 审计日志级别（INFO, DEBUG, WARN），默认为 INFO。
         */
        private String logLevel = "INFO";

        /**
         * 是否对入参中的敏感字段（如 token、password、secret、key 等）自动进行掩码脱敏，默认为 true。
         */
        private boolean maskSensitiveArguments = true;

        /**
         * 获取是否启用审计日志。
         *
         * @return 是否启用审计日志
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用审计日志。
         *
         * @param enabled 是否启用审计日志
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取审计日志级别。
         *
         * @return 日志级别
         */
        public String getLogLevel() {
            return logLevel;
        }

        /**
         * 设置审计日志级别。
         *
         * @param logLevel 日志级别
         */
        public void setLogLevel(String logLevel) {
            this.logLevel = logLevel;
        }

        /**
         * 获取是否掩码脱敏敏感入参。
         *
         * @return 是否掩码脱敏
         */
        public boolean isMaskSensitiveArguments() {
            return maskSensitiveArguments;
        }

        /**
         * 设置是否掩码脱敏敏感入参。
         *
         * @param maskSensitiveArguments 是否掩码脱敏
         */
        public void setMaskSensitiveArguments(boolean maskSensitiveArguments) {
            this.maskSensitiveArguments = maskSensitiveArguments;
        }
    }
}

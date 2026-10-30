package io.github.chukui2005.mcpgov.spring.boot.autoconfigure;

import io.github.chukui2005.mcpgov.core.runtime.policy.AclMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
     * Tool ACL 访问控制相关配置。
     */
    private AclProperties acl = new AclProperties();

    /**
     * 入参校验相关配置。
     */
    private ValidationProperties validation = new ValidationProperties();

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
     * 获取 Tool ACL 配置。
     *
     * @return ACL 配置
     */
    public AclProperties getAcl() {
        return acl;
    }

    /**
     * 设置 Tool ACL 配置。
     *
     * @param acl ACL 配置
     */
    public void setAcl(AclProperties acl) {
        this.acl = acl;
    }

    /**
     * 获取入参校验配置。
     *
     * @return 入参校验配置
     */
    public ValidationProperties getValidation() {
        return validation;
    }

    /**
     * 设置入参校验配置。
     *
     * @param validation 入参校验配置
     */
    public void setValidation(ValidationProperties validation) {
        this.validation = validation;
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

    /**
     * Tool ACL 访问控制配置属性。
     */
    public static class AclProperties {

        /**
         * 是否启用 Tool ACL 拦截治理，默认为 true。
         */
        private boolean enabled = true;

        /**
         * 默认匹配模式：WHITELIST (白名单允许/默认拒绝) 或 BLACKLIST (黑名单拦截/默认放行)。
         */
        private AclMode defaultMode = AclMode.WHITELIST;

        /**
         * 允许调用的工具名称列表或通配符规则。
         */
        private List<String> allowedTools = new ArrayList<>();

        /**
         * 禁止调用的工具名称列表或通配符规则。
         */
        private List<String> blockedTools = new ArrayList<>();

        /**
         * 获取是否启用 Tool ACL 拦截。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 Tool ACL 拦截。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取默认 ACL 匹配模式。
         *
         * @return 匹配模式
         */
        public AclMode getDefaultMode() {
            return defaultMode;
        }

        /**
         * 设置默认 ACL 匹配模式。
         *
         * @param defaultMode 匹配模式
         */
        public void setDefaultMode(AclMode defaultMode) {
            this.defaultMode = defaultMode;
        }

        /**
         * 获取允许调用的工具列表。
         *
         * @return 工具名称或通配规则列表
         */
        public List<String> getAllowedTools() {
            return allowedTools;
        }

        /**
         * 设置允许调用的工具列表。
         *
         * @param allowedTools 工具名称或通配规则列表
         */
        public void setAllowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
        }

        /**
         * 获取禁止调用的工具列表。
         *
         * @return 工具名称或通配规则列表
         */
        public List<String> getBlockedTools() {
            return blockedTools;
        }

        /**
         * 设置禁止调用的工具列表。
         *
         * @param blockedTools 工具名称或通配规则列表
         */
        public void setBlockedTools(List<String> blockedTools) {
            this.blockedTools = blockedTools;
        }
    }

    /**
     * 入参校验配置属性。
     */
    public static class ValidationProperties {

        /**
         * 是否启用参数校验拦截，默认为 false。
         */
        private boolean enabled = false;

        /**
         * 参数校验规则列表。
         */
        private List<RuleProperties> rules = new ArrayList<>();

        /**
         * 获取是否启用参数校验拦截。
         *
         * @return 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用参数校验拦截。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取参数校验规则列表。
         *
         * @return 规则列表
         */
        public List<RuleProperties> getRules() {
            return rules;
        }

        /**
         * 设置参数校验规则列表。
         *
         * @param rules 规则列表
         */
        public void setRules(List<RuleProperties> rules) {
            this.rules = rules;
        }

        /**
         * 单条参数校验规则配置属性。
         */
        public static class RuleProperties {

            /**
             * 适用的工具名称模式（支持 '*' 通配），默认为 '*'。
             */
            private String toolPattern = "*";

            /**
             * 待校验的入参键名。
             */
            private String parameter;

            /**
             * 是否为必填入参，默认为 false。
             */
            private boolean required = false;

            /**
             * 违规或禁用值的正则表达式（命中则阻断）。
             */
            private String disallowedRegex;

            /**
             * 必须满足的正则表达式（未命中则阻断）。
             */
            private String requiredRegex;

            /**
             * 校验未通过时的安全阻断提示。
             */
            private String errorMessage;

            /**
             * 获取适用的工具模式。
             *
             * @return 工具模式
             */
            public String getToolPattern() {
                return toolPattern;
            }

            /**
             * 设置适用的工具模式。
             *
             * @param toolPattern 工具模式
             */
            public void setToolPattern(String toolPattern) {
                this.toolPattern = toolPattern;
            }

            /**
             * 获取待校验的参数名。
             *
             * @return 参数名
             */
            public String getParameter() {
                return parameter;
            }

            /**
             * 设置待校验的参数名。
             *
             * @param parameter 参数名
             */
            public void setParameter(String parameter) {
                this.parameter = parameter;
            }

            /**
             * 获取是否必填。
             *
             * @return 是否必填
             */
            public boolean isRequired() {
                return required;
            }

            /**
             * 设置是否必填。
             *
             * @param required 是否必填
             */
            public void setRequired(boolean required) {
                this.required = required;
            }

            /**
             * 获取违规禁用的正则表达式。
             *
             * @return 禁用正则
             */
            public String getDisallowedRegex() {
                return disallowedRegex;
            }

            /**
             * 设置违规禁用的正则表达式。
             *
             * @param disallowedRegex 禁用正则
             */
            public void setDisallowedRegex(String disallowedRegex) {
                this.disallowedRegex = disallowedRegex;
            }

            /**
             * 获取必须满足的正则表达式。
             *
             * @return 必选正则
             */
            public String getRequiredRegex() {
                return requiredRegex;
            }

            /**
             * 设置必须满足的正则表达式。
             *
             * @param requiredRegex 必选正则
             */
            public void setRequiredRegex(String requiredRegex) {
                this.requiredRegex = requiredRegex;
            }

            /**
             * 获取校验失败提示信息。
             *
             * @return 错误提示信息
             */
            public String getErrorMessage() {
                return errorMessage;
            }

            /**
             * 设置校验失败提示信息。
             *
             * @param errorMessage 错误提示信息
             */
            public void setErrorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
            }
        }
    }
}

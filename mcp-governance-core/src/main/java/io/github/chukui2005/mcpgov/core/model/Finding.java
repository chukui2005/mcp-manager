package io.github.chukui2005.mcpgov.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 静态扫描发现项 (Finding)。
 * 严格对齐 SARIF 2.1.0 契约模型与 OWASP 类别。
 *
 * @param ruleId 规则 ID (例如 MCP01-001)
 * @param category OWASP MCP Top 10 分类
 * @param severity 严重级别
 * @param message 描述信息 (严禁包含敏感 Token 或私钥明文)
 * @param remediation 修复建议
 * @param locations 命中的物理位置列表
 * @param targetServer 目标 MCP Server 名称 (可选)
 * @param targetTool 目标 Tool 名称 (可选)
 * @param sha256 关联对象的 SHA-256 指纹 (可选)
 * @param riskTags 关联的风险标签集
 */
public record Finding(
        String ruleId,
        OwaspCategory category,
        Severity severity,
        String message,
        String remediation,
        List<PhysicalLocation> locations,
        String targetServer,
        String targetTool,
        String sha256,
        Set<String> riskTags
) {
    /**
     * Finding 紧凑构造器
     */
    public Finding {
        Objects.requireNonNull(ruleId, "ruleId 不能为空");
        Objects.requireNonNull(category, "category 不能为空");
        Objects.requireNonNull(severity, "severity 不能为空");
        Objects.requireNonNull(message, "message 不能为空");
        locations = locations != null ? List.copyOf(locations) : Collections.emptyList();
        riskTags = riskTags != null ? Set.copyOf(riskTags) : Collections.emptySet();
    }

    /**
     * 创建构造器
     *
     * @return FindingBuilder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Finding 建造者模式实现
     */
    public static class Builder {
        private String ruleId;
        private OwaspCategory category;
        private Severity severity;
        private String message;
        private String remediation;
        private List<PhysicalLocation> locations;
        private String targetServer;
        private String targetTool;
        private String sha256;
        private Set<String> riskTags;

        /**
         * 设置规则 ID
         *
         * @param ruleId 规则 ID
         * @return 当前 Builder
         */
        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        /**
         * 设置 OWASP 分类
         *
         * @param category 分类
         * @return 当前 Builder
         */
        public Builder category(OwaspCategory category) {
            this.category = category;
            return this;
        }

        /**
         * 设置严重级别
         *
         * @param severity 级别
         * @return 当前 Builder
         */
        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        /**
         * 设置描述信息
         *
         * @param message 描述
         * @return 当前 Builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 设置修复建议
         *
         * @param remediation 修复指导
         * @return 当前 Builder
         */
        public Builder remediation(String remediation) {
            this.remediation = remediation;
            return this;
        }

        /**
         * 设置物理位置列表
         *
         * @param locations 位置列表
         * @return 当前 Builder
         */
        public Builder locations(List<PhysicalLocation> locations) {
            this.locations = locations;
            return this;
        }

        /**
         * 设置目标 Server
         *
         * @param targetServer Server 名称
         * @return 当前 Builder
         */
        public Builder targetServer(String targetServer) {
            this.targetServer = targetServer;
            return this;
        }

        /**
         * 设置目标 Tool
         *
         * @param targetTool Tool 名称
         * @return 当前 Builder
         */
        public Builder targetTool(String targetTool) {
            this.targetTool = targetTool;
            return this;
        }

        /**
         * 设置 SHA-256 指纹
         *
         * @param sha256 指纹
         * @return 当前 Builder
         */
        public Builder sha256(String sha256) {
            this.sha256 = sha256;
            return this;
        }

        /**
         * 设置风险标签集
         *
         * @param riskTags 风险标签
         * @return 当前 Builder
         */
        public Builder riskTags(Set<String> riskTags) {
            this.riskTags = riskTags;
            return this;
        }

        /**
         * 构建 Finding 实例
         *
         * @return Finding
         */
        public Finding build() {
            return new Finding(
                    ruleId,
                    category,
                    severity,
                    message,
                    remediation,
                    locations,
                    targetServer,
                    targetTool,
                    sha256,
                    riskTags
            );
        }
    }
}

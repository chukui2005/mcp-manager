package io.github.chukui2005.mcpgov.core.model;

/**
 * 安全风险严重等级。
 */
public enum Severity {

    /**
     * 阻断级风险，必须立即修复
     */
    CRITICAL("error"),

    /**
     * 高危风险
     */
    HIGH("error"),

    /**
     * 中危风险
     */
    MEDIUM("warning"),

    /**
     * 低危风险
     */
    LOW("note"),

    /**
     * 提示与信息
     */
    INFO("none");

    private final String sarifLevel;

    Severity(String sarifLevel) {
        this.sarifLevel = sarifLevel;
    }

    /**
     * 获取映射到 SARIF 2.1.0 的 level 字段值 (error, warning, note, none)
     *
     * @return SARIF 规范对应的级别字符串
     */
    public String getSarifLevel() {
        return sarifLevel;
    }
}

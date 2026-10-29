package io.github.chukui2005.mcpgov.core.spi;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.Severity;

import java.util.List;

/**
 * 静态扫描规则统一 SPI 接口。
 * 遵循 OWASP MCP Top 10 标准分类。
 */
public interface ScanRule {

    /**
     * 获取规则唯一标识。
     * 格式约束：MCP{01..10}-{001..999}，如 "MCP01-001"。
     *
     * @return 规则 ID
     */
    String getId();

    /**
     * 获取规则中文可读名称。
     *
     * @return 规则名称
     */
    String getName();

    /**
     * 获取规则短描述。
     *
     * @return 规则简短描述
     */
    String getShortDescription();

    /**
     * 获取关联的 OWASP MCP Top 10 分类。
     *
     * @return OWASP 分类
     */
    OwaspCategory getCategory();

    /**
     * 获取规则默认严重级别。
     *
     * @return 严重级别
     */
    Severity getDefaultSeverity();

    /**
     * 获取官方中文修复建议 (Remediation)。
     *
     * @return 修复指导说明
     */
    String getRemediation();

    /**
     * 对给定的扫描上下文执行评估。
     *
     * @param context 扫描上下文
     * @return 扫描命中的 Finding 列表，若无违规返回空列表
     */
    List<Finding> scan(ScanContext context);
}

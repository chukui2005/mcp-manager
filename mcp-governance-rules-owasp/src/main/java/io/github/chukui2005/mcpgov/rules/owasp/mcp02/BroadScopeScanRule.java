package io.github.chukui2005.mcpgov.rules.owasp.mcp02;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;

import java.util.*;
import java.util.regex.Pattern;

/**
 * MCP02-001: 过度宽泛权限与参数范围检测规则。
 * 针对 MCP Tool 的 inputSchema 及名称进行静态分析：
 * 1. 通配符滥用检查：默认值包含全通配符 "*"、"**" 或正则模式为 ".*"；
 * 2. 高危破坏性系统操作模式：工具名称或参数名命中破坏性动词（exec, eval, shell, command, delete, drop 等），且 inputSchema 未作有效限制。
 */
public class BroadScopeScanRule implements ScanRule {

    private static final String RULE_ID = "MCP02-001";

    private static final Pattern HIGH_RISK_OP_PATTERN = Pattern.compile(
            "(?i).*(exec|eval|shell|command|delete|drop|format|rmdir).*"
    );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getName() {
        return "过度宽泛工具权限与通配符范围检测";
    }

    @Override
    public String getShortDescription() {
        return "检测 MCP Tool 参数 Schema 中过度放权的通配符约束或高危破坏性系统操作";
    }

    @Override
    public OwaspCategory getCategory() {
        return OwaspCategory.MCP02;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.HIGH;
    }

    @Override
    public String getRemediation() {
        return "对工具参数定义严格的 enum 枚举白名单、正则格式约束或具体子路径范围，禁止使用全通配符 '*'，对破坏性系统操作增加鉴权门禁。";
    }

    @Override
    public List<Finding> scan(ScanContext context) {
        List<GovernedTool> tools = context.tools();
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }

        String uri = context.targetPath() != null ? context.targetPath().toString() : "mcp.json";
        List<Finding> findings = new ArrayList<>();

        for (GovernedTool tool : tools) {
            // 维度 1: 检查 inputSchema 是否存在过度宽泛的通配符
            checkWildcards(tool, uri, findings);

            // 维度 2: 检查高危操作缺乏约束
            checkHighRiskUnconstrainedOps(tool, uri, findings);
        }

        return findings;
    }

    private void checkWildcards(GovernedTool tool, String uri, List<Finding> findings) {
        Map<String, Object> schema = tool.inputSchema();
        if (schema == null || schema.isEmpty()) {
            return;
        }

        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String paramName = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> paramDef) {
                    // 检查 default 是否为通配符
                    Object defaultVal = paramDef.get("default");
                    if (isWildcard(defaultVal)) {
                        findings.add(createFinding(tool, uri,
                                "工具 [" + tool.name() + "] 的参数 [" + paramName + "] 默认值配置为全通配符 [" + defaultVal + "]，存在过度授权风险"));
                    }

                    // 检查 pattern 是否为宽松无界通配正则
                    Object patternVal = paramDef.get("pattern");
                    if (patternVal != null && (".*".equals(patternVal) || "^.*$".equals(patternVal))) {
                        findings.add(createFinding(tool, uri,
                                "工具 [" + tool.name() + "] 的参数 [" + paramName + "] 正则 pattern 配置为全匹配通配符 [" + patternVal + "]，缺乏有效校验边界"));
                    }
                }
            }
        }
    }

    private void checkHighRiskUnconstrainedOps(GovernedTool tool, String uri, List<Finding> findings) {
        if (!HIGH_RISK_OP_PATTERN.matcher(tool.name()).matches()) {
            return;
        }

        Map<String, Object> schema = tool.inputSchema();
        if (schema == null || schema.isEmpty()) {
            findings.add(createFinding(tool, uri,
                    "高危操作工具 [" + tool.name() + "] 缺失 inputSchema 参数约束定义，具有直接系统级破坏风险"));
            return;
        }

        Object propertiesObj = schema.get("properties");
        boolean hasConstraints = false;
        if (propertiesObj instanceof Map<?, ?> properties && !properties.isEmpty()) {
            for (Object propDef : properties.values()) {
                if (propDef instanceof Map<?, ?> map && (map.containsKey("enum") || map.containsKey("pattern"))) {
                    hasConstraints = true;
                    break;
                }
            }
        }

        if (!hasConstraints) {
            findings.add(createFinding(tool, uri,
                    "高危操作工具 [" + tool.name() + "] 参数中未定义任何 enum 或正则白名单约束，易导致任意命令注入与过度授权"));
        }
    }

    private boolean isWildcard(Object val) {
        if (val instanceof String s) {
            return "*".equals(s) || "**".equals(s);
        }
        return false;
    }

    private Finding createFinding(GovernedTool tool, String uri, String message) {
        return Finding.builder()
                .ruleId(getId())
                .category(getCategory())
                .severity(getDefaultSeverity())
                .message(message)
                .remediation(getRemediation())
                .locations(List.of(PhysicalLocation.of(uri, 1)))
                .riskTags(Set.of("BROAD_SCOPE", "MCP02"))
                .build();
    }
}

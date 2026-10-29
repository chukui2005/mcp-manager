package io.github.chukui2005.mcpgov.rules.owasp.mcp01;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP01-001: 明文密钥与 Token 暴露检测规则。
 * 遵循安全基线：不得在报告中打印 secret 明文值。
 */
public class PlaintextTokenScanRule implements ScanRule {

    private static final String RULE_ID = "MCP01-001";
    private static final Pattern SECRET_KEY_PATTERN = Pattern.compile(
            "(?i)\"(api[_-]?key|secret|token|password|auth_token)\"\\s*:\\s*\"([^$\"]+)\""
    );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getName() {
        return "明文凭证与密钥暴露检测";
    }

    @Override
    public String getShortDescription() {
        return "检测配置文件中硬编码的明文 API 秘钥、访问 Token 与敏感凭据";
    }

    @Override
    public OwaspCategory getCategory() {
        return OwaspCategory.MCP01;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.CRITICAL;
    }

    @Override
    public String getRemediation() {
        return "所有凭证必须通过引用注入（例如 ${env:VAR_NAME}），严禁明文提交任何密钥。";
    }

    @Override
    public List<Finding> scan(ScanContext context) {
        if (context.rawContent() == null || context.rawContent().isBlank()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        String[] lines = context.rawContent().split("\r?\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = SECRET_KEY_PATTERN.matcher(line);
            if (matcher.find()) {
                String keyName = matcher.group(1);
                String uri = context.targetPath() != null ? context.targetPath().toString() : "mcp.json";
                findings.add(Finding.builder()
                        .ruleId(getId())
                        .category(getCategory())
                        .severity(getDefaultSeverity())
                        .message("在配置键 [" + keyName + "] 处检测到硬编码明文凭证（已自动脱敏，不打印秘钥实际值）")
                        .remediation(getRemediation())
                        .locations(List.of(PhysicalLocation.of(uri, i + 1)))
                        .riskTags(Set.of("CREDENTIAL_LEAK", "MCP01"))
                        .build());
            }
        }

        return findings;
    }
}

package io.github.chukui2005.mcpgov.rules.owasp.mcp01;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP01-001 规则测试。
 */
class PlaintextTokenScanRuleTest {

    private final PlaintextTokenScanRule rule = new PlaintextTokenScanRule();

    @Test
    @DisplayName("应该检测到明文 API Token 且不泄漏具体 Secret 值")
    void shouldDetectPlaintextTokenWithoutLeakingValue() {
        String badConfig = """
                {
                  "mcpServers": {
                    "github": {
                      "env": {
                        "api_key": "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
                      }
                    }
                  }
                }
                """;

        ScanContext context = ScanContext.ofFile(Path.of("mcp.json"), badConfig);
        List<Finding> findings = rule.scan(context);

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("MCP01-001");
        assertThat(finding.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(finding.message()).doesNotContain("ghp_1234567890");
        assertThat(finding.locations().get(0).startLine()).isEqualTo(5);
    }

    @Test
    @DisplayName("环境变量引用格式不应该触发告警")
    void shouldNotAlertOnEnvReference() {
        String safeConfig = """
                {
                  "mcpServers": {
                    "github": {
                      "env": {
                        "api_key": "${env:GITHUB_TOKEN}"
                      }
                    }
                  }
                }
                """;

        ScanContext context = ScanContext.ofFile(Path.of("mcp.json"), safeConfig);
        List<Finding> findings = rule.scan(context);

        assertThat(findings).isEmpty();
    }
}

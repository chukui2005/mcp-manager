package io.github.chukui2005.mcpgov.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finding 模型测试。
 */
class FindingTest {

    @Test
    @DisplayName("应该正确构建 Finding 并保持集合不可变")
    void shouldBuildFindingCorrectly() {
        Finding finding = Finding.builder()
                .ruleId("MCP01-001")
                .category(OwaspCategory.MCP01)
                .severity(Severity.CRITICAL)
                .message("发现硬编码 API Token")
                .remediation("请使用环境变量注入 ${env:API_KEY}")
                .locations(List.of(PhysicalLocation.of("mcp.json", 12)))
                .targetServer("filesystem-server")
                .riskTags(Set.of("CREDENTIAL_LEAK"))
                .build();

        assertThat(finding.ruleId()).isEqualTo("MCP01-001");
        assertThat(finding.severity().getSarifLevel()).isEqualTo("error");
        assertThat(finding.locations()).hasSize(1);
        assertThat(finding.locations().get(0).startLine()).isEqualTo(12);
        assertThat(finding.riskTags()).contains("CREDENTIAL_LEAK");
    }
}

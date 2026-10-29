package io.github.chukui2005.mcpgov.scanner.sarif;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SARIF 序列化器契约测试。
 */
class SarifSerializerTest {

    @Test
    @DisplayName("序列化出的 SARIF 必须符合版本 2.1.0 与核心契约结构")
    void shouldSerializeToSarif210Format() {
        ScanRule mockRule = new ScanRule() {
            @Override
            public String getId() {
                return "MCP01-001";
            }

            @Override
            public String getName() {
                return "明文密钥暴露检测";
            }

            @Override
            public String getShortDescription() {
                return "检测配置文件中硬编码的明文秘钥";
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
                return "采用引用注入，如 ${env:SECRET}";
            }

            @Override
            public List<Finding> scan(ScanContext context) {
                return List.of();
            }
        };

        Finding finding = Finding.builder()
                .ruleId("MCP01-001")
                .category(OwaspCategory.MCP01)
                .severity(Severity.CRITICAL)
                .message("检测到高熵 API Token 硬编码")
                .remediation("采用引用注入")
                .locations(List.of(PhysicalLocation.of("mcp.json", 15)))
                .targetServer("test-server")
                .sha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .riskTags(Set.of("CREDENTIAL"))
                .build();

        SarifSerializer serializer = new SarifSerializer();
        String json = serializer.serialize(List.of(mockRule), List.of(finding));

        assertThat(json).contains("\"version\" : \"2.1.0\"");
        assertThat(json).contains("\"name\" : \"mcp-governance-scanner\"");
        assertThat(json).contains("\"ruleId\" : \"MCP01-001\"");
        assertThat(json).contains("\"level\" : \"error\"");
        assertThat(json).contains("\"uri\" : \"mcp.json\"");
        assertThat(json).contains("\"startLine\" : 15");
        assertThat(json).contains("\"sha256\" : \"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"");
    }
}

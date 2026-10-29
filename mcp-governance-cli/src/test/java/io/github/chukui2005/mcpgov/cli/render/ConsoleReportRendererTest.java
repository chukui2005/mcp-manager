package io.github.chukui2005.mcpgov.cli.render;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConsoleReportRenderer 控制台报告渲染器测试")
class ConsoleReportRendererTest {

    @Test
    @DisplayName("空发现项时输出无违规成功提示")
    void testRenderEmptyFindings() {
        ConsoleReportRenderer renderer = new ConsoleReportRenderer(false);
        String report = renderer.renderToString("mcp.json", List.of(), 15);

        assertTrue(report.contains("# MCP 安全静态治理审计报告"));
        assertTrue(report.contains("**审计目标**: mcp.json"));
        assertTrue(report.contains("**发现总数**: 0"));
        assertTrue(report.contains("未检测到任何安全违规或漏洞配置"));
    }

    @Test
    @DisplayName("存在发现项时渲染统计表格与详细清单")
    void testRenderWithFindings() {
        Finding finding = Finding.builder()
                .ruleId("MCP01-001")
                .category(OwaspCategory.MCP01)
                .severity(Severity.CRITICAL)
                .message("硬编码明文密钥")
                .remediation("请使用环境变量引用")
                .locations(List.of(PhysicalLocation.of("mcp.json", 10)))
                .riskTags(Set.of("CREDENTIAL_LEAK"))
                .build();

        ConsoleReportRenderer renderer = new ConsoleReportRenderer(false);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);

        renderer.render(ps, "mcp.json", List.of(finding), 25);
        String output = baos.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("CRITICAL: 1"));
        assertTrue(output.contains("MCP01"));
        assertTrue(output.contains("硬编码明文密钥"));
        assertTrue(output.contains("mcp.json:10"));
        assertTrue(output.contains("**修复建议**: 请使用环境变量引用"));
    }

    @Test
    @DisplayName("ANSI 彩色启用时输出 ANSI 转义序列")
    void testRenderWithAnsi() {
        Finding finding = Finding.builder()
                .ruleId("MCP03-001")
                .category(OwaspCategory.MCP03)
                .severity(Severity.CRITICAL)
                .message("提示注入载荷")
                .build();

        ConsoleReportRenderer renderer = new ConsoleReportRenderer(true);
        String report = renderer.renderToString("mcp.json", List.of(finding), 10);

        assertTrue(report.contains("\u001B[1;31m")); // RED_BOLD
        assertTrue(report.contains("\u001B[0m"));    // RESET
    }
}

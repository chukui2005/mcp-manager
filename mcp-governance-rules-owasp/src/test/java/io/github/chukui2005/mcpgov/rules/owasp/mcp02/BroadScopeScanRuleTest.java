package io.github.chukui2005.mcpgov.rules.owasp.mcp02;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BroadScopeScanRule (MCP02-001) 单元测试")
class BroadScopeScanRuleTest {

    private final BroadScopeScanRule rule = new BroadScopeScanRule();

    @Test
    @DisplayName("测试通配符 default 为 * 触发告警")
    void testWildcardDefaultDetected() {
        GovernedTool tool = GovernedTool.of("list_files", "列出目录内容", Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of(
                                "type", "string",
                                "default", "*"
                        )
                )
        ));

        ScanContext context = ScanContext.ofTools(List.of(tool));
        List<Finding> findings = rule.scan(context);

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals("MCP02-001", finding.ruleId());
        assertEquals(OwaspCategory.MCP02, finding.category());
        assertEquals(Severity.HIGH, finding.severity());
        assertTrue(finding.message().contains("全通配符"));
    }

    @Test
    @DisplayName("测试高危系统操作缺乏约束触发告警")
    void testHighRiskUnconstrainedOpDetected() {
        GovernedTool tool = GovernedTool.of("shell_exec", "执行系统命令", Map.of(
                "type", "object",
                "properties", Map.of(
                        "cmd", Map.of("type", "string")
                )
        ));

        ScanContext context = ScanContext.ofTools(List.of(tool));
        List<Finding> findings = rule.scan(context);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.message().contains("高危操作工具")));
    }

    @Test
    @DisplayName("良性具约束工具不产生告警")
    void testSafeToolWithConstraints() {
        GovernedTool safeTool = GovernedTool.of("get_user", "查询用户信息", Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of(
                                "type", "string",
                                "pattern", "^[0-9a-fA-F]{8}$"
                        )
                )
        ));

        ScanContext context = ScanContext.ofTools(List.of(safeTool));
        List<Finding> findings = rule.scan(context);

        assertTrue(findings.isEmpty());
    }
}

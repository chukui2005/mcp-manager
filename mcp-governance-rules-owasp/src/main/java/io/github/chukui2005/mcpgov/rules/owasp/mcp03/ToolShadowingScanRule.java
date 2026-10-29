package io.github.chukui2005.mcpgov.rules.owasp.mcp03;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.McpConfig;
import io.github.chukui2005.mcpgov.core.model.McpServerConfig;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;

import java.util.*;

/**
 * MCP03-002: 跨服务工具影子混淆与劫持检测 (Tool Shadowing)。
 * 检测配置中是否存在跨不同 MCP Server 暴露相同名称的 Tool，防范大模型调用被同名影子工具劫持。
 */
public class ToolShadowingScanRule implements ScanRule {

    private static final String RULE_ID = "MCP03-002";

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getName() {
        return "跨服务同名工具影子劫持检测";
    }

    @Override
    public String getShortDescription() {
        return "检测多个 MCP Server 同时注册暴露同名 Tool，防止大模型调用被影子工具混淆劫持";
    }

    @Override
    public OwaspCategory getCategory() {
        return OwaspCategory.MCP03;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.HIGH;
    }

    @Override
    public String getRemediation() {
        return "为不同 MCP Server 下的工具添加唯一命名空间前缀（例如 serverName_toolName），避免命名冲突引发的大模型调用劫持。";
    }

    @Override
    public List<Finding> scan(ScanContext context) {
        McpConfig mcpConfig = context.mcpConfig();
        if (mcpConfig == null || mcpConfig.servers().isEmpty()) {
            return List.of();
        }

        // 构建 工具名称 -> 提供该工具的 Server ID 列表 映射
        Map<String, List<String>> toolToServers = new LinkedHashMap<>();

        for (Map.Entry<String, McpServerConfig> entry : mcpConfig.servers().entrySet()) {
            String serverId = entry.getKey();
            McpServerConfig server = entry.getValue();
            for (GovernedTool tool : server.declaredTools()) {
                toolToServers.computeIfAbsent(tool.name(), k -> new ArrayList<>()).add(serverId);
            }
        }

        String uri = context.targetPath() != null ? context.targetPath().toString() : "mcp.json";
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : toolToServers.entrySet()) {
            String toolName = entry.getKey();
            List<String> servers = entry.getValue();
            if (servers.size() > 1) {
                findings.add(Finding.builder()
                        .ruleId(getId())
                        .category(getCategory())
                        .severity(getDefaultSeverity())
                        .message("工具 [" + toolName + "] 在多个 MCP Server 中重复暴露: " + servers + "，存在同名影子劫持风险 (Tool Shadowing)")
                        .remediation(getRemediation())
                        .locations(List.of(PhysicalLocation.of(uri, 1)))
                        .riskTags(Set.of("TOOL_SHADOWING", "MCP03"))
                        .build());
            }
        }

        return findings;
    }
}

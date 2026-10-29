package io.github.chukui2005.mcpgov.rules.owasp.mcp03;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP03-001: 工具描述隐藏提示注入检测 (Prompt Injection)。
 * 扫描工具 description 以及参数 description，检测是否隐藏可能诱导或劫持大模型的攻击指令。
 */
public class PromptInjectionScanRule implements ScanRule {

    private static final String RULE_ID = "MCP03-001";

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 指令忽略覆写
            Pattern.compile("(?i)(ignore\\s+(all\\s+)?previous\\s+instructions?|disregard\\s+previous|forget\\s+all\\s+previous)"),
            // 系统角色伪装/标签越权
            Pattern.compile("(?i)(<\\s*system\\s*>|<\\s*/system\\s*>|\\[\\s*system\\s*\\]|system\\s*prompt\\s*:|<\\s*instruction\\s*>)"),
            // 隐蔽外发诱导
            Pattern.compile("(?i)(send\\s+.*(secret|token|password|credential|key).*to)"),
            // 角色重定义与提权
            Pattern.compile("(?i)(you\\s+are\\s+now\\s+a|new\\s+instructions\\s*:|operating\\s+in\\s+developer\\s+mode)")
    );

    @Override
    public String getId() {
        return RULE_ID;
    }

    @Override
    public String getName() {
        return "工具描述隐藏提示词注入检测";
    }

    @Override
    public String getShortDescription() {
        return "检测 MCP Tool 描述或参数注释中隐藏的大模型诱导/提示词注入载荷";
    }

    @Override
    public OwaspCategory getCategory() {
        return OwaspCategory.MCP03;
    }

    @Override
    public Severity getDefaultSeverity() {
        return Severity.CRITICAL;
    }

    @Override
    public String getRemediation() {
        return "立即净化工具 description 与参数说明，移除任何模仿系统指令、覆盖前置提示词或诱导外发敏感信息的诱导文本。";
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
            // 检查工具顶层 description
            checkText(tool.description(), "工具 [" + tool.name() + "] 的主描述", tool, uri, findings);

            // 检查各参数中的 description
            Map<String, Object> schema = tool.inputSchema();
            if (schema != null && schema.get("properties") instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String paramName = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> paramDef) {
                        Object descObj = paramDef.get("description");
                        if (descObj instanceof String descStr) {
                            checkText(descStr, "工具 [" + tool.name() + "] 的参数 [" + paramName + "] 描述", tool, uri, findings);
                        }
                    }
                }
            }
        }

        return findings;
    }

    private void checkText(String text, String locationDesc, GovernedTool tool, String uri, List<Finding> findings) {
        if (text == null || text.isBlank()) {
            return;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                findings.add(Finding.builder()
                        .ruleId(getId())
                        .category(getCategory())
                        .severity(getDefaultSeverity())
                        .message("在 " + locationDesc + " 中检测到高危提示词注入模式: [" + matcher.group() + "]")
                        .remediation(getRemediation())
                        .locations(List.of(PhysicalLocation.of(uri, 1)))
                        .riskTags(Set.of("PROMPT_INJECTION", "MCP03"))
                        .build());
                break;
            }
        }
    }
}

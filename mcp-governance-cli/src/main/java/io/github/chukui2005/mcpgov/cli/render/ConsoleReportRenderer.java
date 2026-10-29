package io.github.chukui2005.mcpgov.cli.render;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.OwaspCategory;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.model.Severity;

import java.io.PrintStream;
import java.util.*;

/**
 * 控制台 ANSI 彩色 Markdown 报告渲染器。
 * 格式化输出扫描概览卡片、风险统计表及带有严重度颜色高亮的详细安全发现项。
 */
public class ConsoleReportRenderer {

    // ANSI 颜色与样式转义码
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED_BOLD = "\u001B[1;31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String GRAY = "\u001B[90m";
    private static final String GREEN = "\u001B[32m";

    private final boolean ansiEnabled;

    /**
     * 默认构造函数，启用 ANSI 颜色高亮。
     */
    public ConsoleReportRenderer() {
        this(true);
    }

    /**
     * 指定是否启用 ANSI 样式的构造函数。
     *
     * @param ansiEnabled 是否启用 ANSI 终端彩色转义
     */
    public ConsoleReportRenderer(boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * 渲染完整扫描报告至标准打印流。
     *
     * @param out 打印流 (如 System.out)
     * @param targetPath 目标扫描路径
     * @param findings 发现的安全隐患列表
     * @param elapsedMs 扫描耗时 (毫秒)
     */
    public void render(PrintStream out, String targetPath, List<Finding> findings, long elapsedMs) {
        Objects.requireNonNull(out, "out 打印流不能为空");
        List<Finding> safeFindings = findings != null ? findings : Collections.emptyList();

        out.println(renderToString(targetPath, safeFindings, elapsedMs));
    }

    /**
     * 将扫描结果渲染为格式化的 Markdown 文本字符串。
     *
     * @param targetPath 目标扫描路径
     * @param findings 发现项列表
     * @param elapsedMs 耗时毫秒
     * @return 格式化报告文本
     */
    public String renderToString(String targetPath, List<Finding> findings, long elapsedMs) {
        List<Finding> safeFindings = findings != null ? findings : Collections.emptyList();
        StringBuilder sb = new StringBuilder();

        // 统计各严重级别数量
        long criticalCount = safeFindings.stream().filter(f -> f.severity() == Severity.CRITICAL).count();
        long highCount = safeFindings.stream().filter(f -> f.severity() == Severity.HIGH).count();
        long mediumCount = safeFindings.stream().filter(f -> f.severity() == Severity.MEDIUM).count();
        long lowCount = safeFindings.stream().filter(f -> f.severity() == Severity.LOW).count();

        // 1. 扫描概览卡片
        sb.append(color(BOLD, "# MCP 安全静态治理审计报告\n"));
        sb.append("------------------------------------------------------------------------\n");
        sb.append(String.format("- **审计目标**: %s\n", targetPath != null ? targetPath : "内存配置"));
        sb.append(String.format("- **审计耗时**: %d ms\n", elapsedMs));
        sb.append(String.format("- **发现总数**: %d (CRITICAL: %s, HIGH: %s, MEDIUM: %s, LOW: %s)\n",
                safeFindings.size(),
                color(RED_BOLD, String.valueOf(criticalCount)),
                color(YELLOW, String.valueOf(highCount)),
                color(BLUE, String.valueOf(mediumCount)),
                color(GRAY, String.valueOf(lowCount))));
        sb.append("------------------------------------------------------------------------\n\n");

        if (safeFindings.isEmpty()) {
            sb.append(color(GREEN, "✔ 恭喜！未检测到任何安全违规或漏洞配置。\n"));
            return sb.toString();
        }

        // 2. OWASP 分类风险分布表格
        sb.append(color(BOLD, "### 风险分布汇总 (OWASP 分类)\n\n"));
        sb.append("| OWASP 分类 | 风险项描述 | 发现数 |\n");
        sb.append("| :--- | :--- | :--- |\n");

        Map<OwaspCategory, Long> categoryCounts = new LinkedHashMap<>();
        for (Finding f : safeFindings) {
            categoryCounts.put(f.category(), categoryCounts.getOrDefault(f.category(), 0L) + 1);
        }

        for (Map.Entry<OwaspCategory, Long> entry : categoryCounts.entrySet()) {
            OwaspCategory cat = entry.getKey();
            sb.append(String.format("| %s | %s | %d |\n", cat.name(), cat.getDescription(), entry.getValue()));
        }
        sb.append("\n");

        // 3. 详细发现项清单
        sb.append(color(BOLD, "### 详细安全发现清单\n\n"));
        for (int i = 0; i < safeFindings.size(); i++) {
            Finding f = safeFindings.get(i);
            String severityFormatted = formatSeverity(f.severity());

            sb.append(String.format("%d. [%s] **%s** (%s)\n",
                    i + 1,
                    severityFormatted,
                    f.ruleId(),
                    f.category().name()));
            sb.append(String.format("   - **问题说明**: %s\n", f.message()));

            if (f.locations() != null && !f.locations().isEmpty()) {
                PhysicalLocation loc = f.locations().get(0);
                sb.append(String.format("   - **定位**: `%s:%d`\n", loc.uri(), loc.startLine()));
            }

            if (f.remediation() != null && !f.remediation().isBlank()) {
                sb.append(String.format("   - **修复建议**: %s\n", f.remediation()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatSeverity(Severity severity) {
        if (!ansiEnabled) {
            return severity.name();
        }
        return switch (severity) {
            case CRITICAL -> RED_BOLD + severity.name() + RESET;
            case HIGH -> YELLOW + severity.name() + RESET;
            case MEDIUM -> BLUE + severity.name() + RESET;
            case LOW -> GRAY + severity.name() + RESET;
            case INFO -> GRAY + severity.name() + RESET;
        };
    }

    private String color(String ansiStyle, String text) {
        if (!ansiEnabled) {
            return text;
        }
        return ansiStyle + text + RESET;
    }
}

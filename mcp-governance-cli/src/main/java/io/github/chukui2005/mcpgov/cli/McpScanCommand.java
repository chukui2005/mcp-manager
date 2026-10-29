package io.github.chukui2005.mcpgov.cli;

import io.github.chukui2005.mcpgov.cli.render.ConsoleReportRenderer;
import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.McpConfig;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.rules.owasp.mcp01.PlaintextTokenScanRule;
import io.github.chukui2005.mcpgov.rules.owasp.mcp02.BroadScopeScanRule;
import io.github.chukui2005.mcpgov.rules.owasp.mcp03.PromptInjectionScanRule;
import io.github.chukui2005.mcpgov.rules.owasp.mcp03.ToolShadowingScanRule;
import io.github.chukui2005.mcpgov.scanner.ScanEngine;
import io.github.chukui2005.mcpgov.scanner.parser.McpConfigParser;
import io.github.chukui2005.mcpgov.scanner.sarif.SarifSerializer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * MCP Governance 命令行入口。
 * 负责解析 MCP 配置文件、执行静态扫描、输出 ANSI 报告及导出 SARIF 2.1.0 标准结果。
 */
@Command(
        name = "mcp-scan",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "MCP 原生安全治理与静态扫描器 (遵循 OWASP MCP Top 10 与 SARIF 2.1.0 标准)"
)
public class McpScanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "待扫描的 MCP 配置文件或目录 (如 mcp.json)", arity = "0..1")
    private File targetFile;

    @Option(names = {"-o", "--output"}, description = "SARIF 2.1.0 报告输出文件路径")
    private File outputFile;

    @Option(names = {"--sarif"}, description = "在控制台直接输出 SARIF 2.1.0 JSON 结果")
    private boolean printSarif;

    @Option(names = {"--allow-execute"}, description = "显式允许执行 STDIO 命令以探测动态工具 (高风险)")
    private boolean allowExecute;

    /**
     * 命令行主函数入口。
     *
     * @param args 命令行入参
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new McpScanCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (targetFile == null || !targetFile.exists()) {
            System.err.println("错误: 目标文件不存在或未指定");
            return 1;
        }

        if (allowExecute) {
            System.out.println("警告: 已启用 --allow-execute。请注意 STDIO 子进程可能带来任意代码执行风险 (MCP05)！");
        }

        long startTime = System.currentTimeMillis();
        String content = Files.readString(targetFile.toPath());

        // 解析 MCP 配置文件并构建强类型 McpConfig
        McpConfigParser parser = new McpConfigParser();
        McpConfig mcpConfig = parser.parseString(content);

        // 构建具备强类型配置与内嵌声明工具的 ScanContext
        ScanContext context = ScanContext.ofMcpConfig(targetFile.toPath(), content, mcpConfig);

        // 初始化规则集与扫描引擎 (Phase 1 涵盖 MCP01、MCP02 与 MCP03)
        ScanEngine engine = new ScanEngine(List.of(
                new PlaintextTokenScanRule(),
                new BroadScopeScanRule(),
                new PromptInjectionScanRule(),
                new ToolShadowingScanRule()
        ));

        List<Finding> findings = engine.execute(context);
        long elapsedMs = System.currentTimeMillis() - startTime;

        SarifSerializer serializer = new SarifSerializer();
        String sarifJson = serializer.serialize(engine.getRegisteredRules(), findings);

        if (outputFile != null) {
            Files.writeString(outputFile.toPath(), sarifJson);
            System.out.println("SARIF 报告已生成至: " + outputFile.getAbsolutePath());
        }

        if (printSarif) {
            System.out.println(sarifJson);
        } else {
            ConsoleReportRenderer renderer = new ConsoleReportRenderer();
            renderer.render(System.out, targetFile.getPath(), findings, elapsedMs);
        }

        // CI 门禁检查：若存在 CRITICAL 或 HIGH 级别阻断，返回非零退出码 2
        boolean hasBlockingFailure = findings.stream()
                .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH);

        return hasBlockingFailure ? 2 : 0;
    }
}

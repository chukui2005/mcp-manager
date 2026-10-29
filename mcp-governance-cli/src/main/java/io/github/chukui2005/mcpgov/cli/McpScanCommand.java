package io.github.chukui2005.mcpgov.cli;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.rules.owasp.mcp01.PlaintextTokenScanRule;
import io.github.chukui2005.mcpgov.scanner.ScanEngine;
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

        String content = Files.readString(targetFile.toPath());
        ScanContext context = new ScanContext(targetFile.toPath(), content, null, null, allowExecute);

        // 初始化规则集与引擎
        PlaintextTokenScanRule tokenRule = new PlaintextTokenScanRule();
        ScanEngine engine = new ScanEngine(List.of(tokenRule));

        List<Finding> findings = engine.execute(context);

        SarifSerializer serializer = new SarifSerializer();
        String sarifJson = serializer.serialize(engine.getRegisteredRules(), findings);

        if (outputFile != null) {
            Files.writeString(outputFile.toPath(), sarifJson);
            System.out.println("SARIF 报告已生成至: " + outputFile.getAbsolutePath());
        }

        if (printSarif) {
            System.out.println(sarifJson);
        } else {
            System.out.printf("扫描完成，共发现 %d 个安全问题。%n", findings.size());
            for (Finding f : findings) {
                System.out.printf("[%s] [%s] %s%n", f.severity(), f.ruleId(), f.message());
            }
        }

        // CI 门禁检查：若存在 CRITICAL 或 HIGH 级别阻断，返回非零退出码
        boolean hasBlockingFailure = findings.stream()
                .anyMatch(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH);

        return hasBlockingFailure ? 2 : 0;
    }
}

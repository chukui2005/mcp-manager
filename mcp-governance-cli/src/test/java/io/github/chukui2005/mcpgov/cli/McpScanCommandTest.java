package io.github.chukui2005.mcpgov.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 命令行入口测试。
 */
class McpScanCommandTest {

    @Test
    @DisplayName("当检测到硬编码 Token 时应返回阻断退出码 2 并生成 SARIF 报告")
    void shouldReturnExitCode2WhenCriticalFound(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mcp.json");
        Path sarifFile = tempDir.resolve("scan-result.sarif");

        Files.writeString(configFile, """
                {
                  "mcpServers": {
                    "fs": {
                      "env": {
                        "api_key": "secret_abc_123"
                      }
                    }
                  }
                }
                """);

        CommandLine cmd = new CommandLine(new McpScanCommand());
        int exitCode = cmd.execute(configFile.toString(), "-o", sarifFile.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(Files.exists(sarifFile)).isTrue();
        String sarifContent = Files.readString(sarifFile);
        assertThat(sarifContent).contains("\"version\" : \"2.1.0\"");
        assertThat(sarifContent).contains("\"ruleId\" : \"MCP01-001\"");
    }

    @Test
    @DisplayName("当配置合规时应返回退出码 0")
    void shouldReturnExitCode0WhenSafe(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("safe-mcp.json");

        Files.writeString(configFile, """
                {
                  "mcpServers": {
                    "fs": {
                      "env": {
                        "api_key": "${env:SAFE_TOKEN}"
                      }
                    }
                  }
                }
                """);

        CommandLine cmd = new CommandLine(new McpScanCommand());
        int exitCode = cmd.execute(configFile.toString());

        assertThat(exitCode).isEqualTo(0);
    }
}

package io.github.chukui2005.mcpgov.scanner.parser;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.McpConfig;
import io.github.chukui2005.mcpgov.core.model.McpServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpConfigParser 静态配置解析器测试")
class McpConfigParserTest {

    private final McpConfigParser parser = new McpConfigParser();

    @Test
    @DisplayName("解析标准 Claude Desktop 格式 (带 mcpServers 根节点)")
    void testParseClaudeDesktopFormat() throws Exception {
        String json = """
                {
                  "mcpServers": {
                    "filesystem": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/workspace"],
                      "env": {
                        "CACHE_DIR": "/tmp/mcp"
                      }
                    },
                    "github": {
                      "command": "docker",
                      "args": ["run", "-i", "--rm", "mcp/github"],
                      "env": {
                        "GITHUB_TOKEN": "ghp_1234567890"
                      }
                    }
                  }
                }
                """;

        McpConfig config = parser.parseString(json);
        assertNotNull(config);
        assertEquals(2, config.servers().size());

        McpServerConfig fs = config.servers().get("filesystem");
        assertNotNull(fs);
        assertEquals("filesystem", fs.id());
        assertEquals("npx", fs.command());
        assertEquals(List.of("-y", "@modelcontextprotocol/server-filesystem", "/workspace"), fs.args());
        assertEquals("/tmp/mcp", fs.env().get("CACHE_DIR"));

        McpServerConfig gh = config.servers().get("github");
        assertNotNull(gh);
        assertEquals("ghp_1234567890", gh.env().get("GITHUB_TOKEN"));
    }

    @Test
    @DisplayName("解析带有内嵌 tools 声明的 MCP 配置文件")
    void testParseWithEmbeddedTools() throws Exception {
        String json = """
                {
                  "mcpServers": {
                    "custom_tools": {
                      "command": "python",
                      "args": ["main.py"],
                      "tools": [
                        {
                          "name": "calculate_hash",
                          "description": "计算数据哈希值",
                          "inputSchema": {
                            "type": "object",
                            "properties": {
                              "algorithm": { "type": "string" },
                              "data": { "type": "string" }
                            },
                            "required": ["algorithm", "data"]
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        McpConfig config = parser.parseString(json);
        assertEquals(1, config.servers().size());

        McpServerConfig custom = config.servers().get("custom_tools");
        assertNotNull(custom);
        assertEquals(1, custom.declaredTools().size());

        GovernedTool tool = custom.declaredTools().get(0);
        assertEquals("calculate_hash", tool.name());
        assertEquals("计算数据哈希值", tool.description());
        assertNotNull(tool.inputSchema());
        assertEquals("object", tool.inputSchema().get("type"));
        assertNotNull(tool.sha256());
    }

    @Test
    @DisplayName("从临时文件读取并解析配置")
    void testParseFromFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("mcp.json");
        String content = """
                {
                  "mcpServers": {
                    "local_app": {
                      "command": "node",
                      "args": ["dist/index.js"]
                    }
                  }
                }
                """;
        Files.writeString(configFile, content);

        McpConfig config = parser.parse(configFile);
        assertNotNull(config);
        assertTrue(config.servers().containsKey("local_app"));
    }

    @Test
    @DisplayName("空串与异常格式宽容处理")
    void testEdgeCases() throws Exception {
        McpConfig emptyConfig = parser.parseString("");
        assertTrue(emptyConfig.servers().isEmpty());

        McpConfig nullConfig = parser.parseString(null);
        assertTrue(nullConfig.servers().isEmpty());

        McpConfig emptyObj = parser.parseString("{}");
        assertTrue(emptyObj.servers().isEmpty());
    }
}

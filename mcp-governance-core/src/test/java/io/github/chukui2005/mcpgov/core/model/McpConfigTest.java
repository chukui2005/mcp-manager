package io.github.chukui2005.mcpgov.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpConfig 与 McpServerConfig 不可变模型测试")
class McpConfigTest {

    @Test
    @DisplayName("McpServerConfig 正常创建并保证防御性拷贝")
    void testMcpServerConfigCreation() {
        GovernedTool tool = GovernedTool.of("test_tool", "测试工具", Map.of("type", "object"));
        McpServerConfig server = new McpServerConfig(
                "fs_server",
                "npx",
                List.of("-y", "@modelcontextprotocol/server-filesystem"),
                Map.of("API_KEY", "env-val"),
                List.of(tool)
        );

        assertEquals("fs_server", server.id());
        assertEquals("npx", server.command());
        assertEquals(2, server.args().size());
        assertEquals("env-val", server.env().get("API_KEY"));
        assertEquals(1, server.declaredTools().size());
        assertEquals("test_tool", server.declaredTools().get(0).name());

        // 验证不可变性
        assertThrows(UnsupportedOperationException.class, () -> server.args().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> server.env().put("NEW", "VAL"));
        assertThrows(UnsupportedOperationException.class, () -> server.declaredTools().add(tool));
    }

    @Test
    @DisplayName("McpServerConfig 必填项空值防御")
    void testMcpServerConfigNullProtection() {
        assertThrows(NullPointerException.class, () -> new McpServerConfig(null, "node", null, null, null));

        McpServerConfig defaultNulls = new McpServerConfig("server_id", null, null, null, null);
        assertEquals("", defaultNulls.command());
        assertTrue(defaultNulls.args().isEmpty());
        assertTrue(defaultNulls.env().isEmpty());
        assertTrue(defaultNulls.declaredTools().isEmpty());
    }

    @Test
    @DisplayName("McpConfig 汇聚工具与按名称反查 Server")
    void testMcpConfigAggregationAndLookup() {
        GovernedTool tool1 = GovernedTool.of("tool_a", "描述 A", Map.of("type", "object"));
        GovernedTool tool2 = GovernedTool.of("tool_b", "描述 B", Map.of("type", "object"));
        GovernedTool tool3 = GovernedTool.of("tool_c", "描述 C", Map.of("type", "object"));

        McpServerConfig s1 = new McpServerConfig("server_1", "node", List.of(), Map.of(), List.of(tool1, tool2));
        McpServerConfig s2 = new McpServerConfig("server_2", "python", List.of(), Map.of(), List.of(tool3));

        McpConfig config = new McpConfig(Map.of("server_1", s1, "server_2", s2));

        assertEquals(2, config.servers().size());
        List<GovernedTool> allTools = config.getAllDeclaredTools();
        assertEquals(3, allTools.size());

        // 反查 Server
        assertEquals(Optional.of("server_1"), config.findServerByToolName("tool_a"));
        assertEquals(Optional.of("server_1"), config.findServerByToolName("tool_b"));
        assertEquals(Optional.of("server_2"), config.findServerByToolName("tool_c"));
        assertEquals(Optional.empty(), config.findServerByToolName("not_exist"));
        assertEquals(Optional.empty(), config.findServerByToolName(null));
    }
}

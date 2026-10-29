package io.github.chukui2005.mcpgov.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 配置文件整体抽象模型。
 * 聚合所有声明的 MCP Server 静态配置，并提供跨服务工具检索与归集辅助方法。
 *
 * @param servers 服务配置映射表 (Key: serverId, Value: McpServerConfig，只读不可变)
 */
public record McpConfig(
        Map<String, McpServerConfig> servers
) {
    /**
     * 紧凑构造器，执行不可变防护。
     *
     * @param servers 服务配置映射表
     */
    public McpConfig {
        servers = servers != null ? Map.copyOf(servers) : Collections.emptyMap();
    }

    /**
     * 汇聚获取当前配置内所有 Server 声明的受控工具。
     *
     * @return 不可变的工具列表
     */
    public List<GovernedTool> getAllDeclaredTools() {
        List<GovernedTool> list = new ArrayList<>();
        for (McpServerConfig server : servers.values()) {
            list.addAll(server.declaredTools());
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 根据工具名称定位首个所属的 Server ID。
     *
     * @param toolName 工具名称
     * @return 所属服务 ID（若存在）
     */
    public Optional<String> findServerByToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
            for (GovernedTool tool : entry.getValue().declaredTools()) {
                if (toolName.equals(tool.name())) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }
}

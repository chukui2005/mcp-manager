package io.github.chukui2005.mcpgov.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单个 MCP Server 静态配置实体。
 * 表示从 MCP 配置文件（如 mcp.json 或 claude_desktop_config.json）中解析出的单一服务端点配置。
 *
 * @param id 服务唯一标识名称 (非空，如 "filesystem", "github")
 * @param command 启动命令 (如 "npx", "docker"，仅用于静态审计，默认禁止执行)
 * @param args 命令参数列表 (只读不可变)
 * @param env 环境变量表 (只读不可变)
 * @param declaredTools 静态声明的受控工具列表 (只读不可变)
 */
public record McpServerConfig(
        String id,
        String command,
        List<String> args,
        Map<String, String> env,
        List<GovernedTool> declaredTools
) {
    /**
     * 紧凑构造器，执行非空验证与不可变集合防护。
     *
     * @param id 服务唯一标识名称
     * @param command 启动命令
     * @param args 参数列表
     * @param env 环境变量表
     * @param declaredTools 静态声明的受控工具列表
     */
    public McpServerConfig {
        Objects.requireNonNull(id, "服务标识 (id) 不能为空");
        command = command != null ? command : "";
        args = args != null ? List.copyOf(args) : Collections.emptyList();
        env = env != null ? Map.copyOf(env) : Collections.emptyMap();
        declaredTools = declaredTools != null ? List.copyOf(declaredTools) : Collections.emptyList();
    }
}

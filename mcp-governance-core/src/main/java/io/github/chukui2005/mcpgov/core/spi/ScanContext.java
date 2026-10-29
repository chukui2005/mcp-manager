package io.github.chukui2005.mcpgov.core.spi;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.McpConfig;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 静态扫描上下文。
 * 封装待分析的文件路径、配置原始文本、解析后的 Map 结构、强类型 McpConfig 以及受控工具列表。
 *
 * @param targetPath 待扫描的目标物理文件路径 (可能为 null，例如纯内存扫描)
 * @param rawContent 目标配置文件或模式定义的原始内容文本 (可能为 null)
 * @param parsedConfig 已解析的配置树结构 (只读 Map，防空)
 * @param mcpConfig 已解析的强类型 MCP 配置实体 (只读，防空)
 * @param tools 从当前配置或定义中解析出的 GovernedTool 列表 (只读不可变)
 * @param executionAllowed 是否允许探测执行 (Phase 1 默认为 false，防范 MCP05)
 */
public record ScanContext(
        Path targetPath,
        String rawContent,
        Map<String, Object> parsedConfig,
        McpConfig mcpConfig,
        List<GovernedTool> tools,
        boolean executionAllowed
) {
    /**
     * 紧凑构造器，保证内部集合与实体只读防空。
     *
     * @param targetPath 目标文件路径
     * @param rawContent 原始内容文本
     * @param parsedConfig 解析后的 Map 配置
     * @param mcpConfig 强类型 MCP 配置模型
     * @param tools 受控工具列表
     * @param executionAllowed 是否允许执行
     */
    public ScanContext {
        parsedConfig = parsedConfig != null ? Map.copyOf(parsedConfig) : Collections.emptyMap();
        mcpConfig = mcpConfig != null ? mcpConfig : new McpConfig(Collections.emptyMap());
        tools = tools != null ? List.copyOf(tools) : Collections.emptyList();
    }

    /**
     * 向下兼容的 5 参数构造器。
     *
     * @param targetPath 目标物理文件路径
     * @param rawContent 原始内容文本
     * @param parsedConfig 已解析的配置树结构
     * @param tools 受控工具列表
     * @param executionAllowed 是否允许探测执行
     */
    public ScanContext(Path targetPath, String rawContent, Map<String, Object> parsedConfig, List<GovernedTool> tools, boolean executionAllowed) {
        this(targetPath, rawContent, parsedConfig, new McpConfig(Collections.emptyMap()), tools, executionAllowed);
    }

    /**
     * 创建纯文件上下文。
     *
     * @param targetPath 文件路径
     * @param rawContent 文件内容
     * @return 扫描上下文
     */
    public static ScanContext ofFile(Path targetPath, String rawContent) {
        return new ScanContext(targetPath, rawContent, Collections.emptyMap(), new McpConfig(Collections.emptyMap()), Collections.emptyList(), false);
    }

    /**
     * 创建工具集上下文。
     *
     * @param tools 受控工具列表
     * @return 扫描上下文
     */
    public static ScanContext ofTools(List<GovernedTool> tools) {
        return new ScanContext(null, null, Collections.emptyMap(), new McpConfig(Collections.emptyMap()), tools, false);
    }

    /**
     * 创建包含强类型 McpConfig 的全量上下文。
     *
     * @param targetPath 文件路径
     * @param rawContent 文件原始内容
     * @param mcpConfig 强类型 MCP 配置模型
     * @return 扫描上下文
     */
    public static ScanContext ofMcpConfig(Path targetPath, String rawContent, McpConfig mcpConfig) {
        List<GovernedTool> declaredTools = mcpConfig != null ? mcpConfig.getAllDeclaredTools() : Collections.emptyList();
        return new ScanContext(targetPath, rawContent, Collections.emptyMap(), mcpConfig, declaredTools, false);
    }
}

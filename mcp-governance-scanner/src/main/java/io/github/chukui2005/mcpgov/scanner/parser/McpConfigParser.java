package io.github.chukui2005.mcpgov.scanner.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.McpConfig;
import io.github.chukui2005.mcpgov.core.model.McpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * MCP 配置文件静态解析器。
 * 支持标准 mcp.json 以及 Claude Desktop 配置文件 (claude_desktop_config.json) 的静态语法树解析。
 * 安全声明：严格执行纯静态 AST 解析，默认绝不拉起外部进程或调用 STDIO 执行（防范 MCP05）。
 */
public class McpConfigParser {

    private static final Logger log = LoggerFactory.getLogger(McpConfigParser.class);
    private final ObjectMapper objectMapper;

    /**
     * 默认构造函数，初始化配置解析器。
     */
    public McpConfigParser() {
        this(new ObjectMapper());
    }

    /**
     * 指定 ObjectMapper 的构造函数。
     *
     * @param objectMapper Jackson 映射器
     */
    public McpConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    /**
     * 从物理文件路径静态解析 MCP 配置。
     *
     * @param configPath 配置文件路径
     * @return 解析后的 MCP 配置实体
     * @throws IOException 文件读取或解析失败时抛出
     */
    public McpConfig parse(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "配置文件路径 (configPath) 不能为空");
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("配置文件不存在: " + configPath);
        }
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        return parseString(content);
    }

    /**
     * 从 JSON 字符串静态解析 MCP 配置。
     *
     * @param jsonContent JSON 文本内容
     * @return 解析后的 MCP 配置实体
     * @throws JsonProcessingException JSON 语法有误时抛出
     */
    public McpConfig parseString(String jsonContent) throws JsonProcessingException {
        if (jsonContent == null || jsonContent.isBlank()) {
            return new McpConfig(Collections.emptyMap());
        }

        JsonNode rootNode = objectMapper.readTree(jsonContent);
        if (rootNode == null || !rootNode.isObject()) {
            return new McpConfig(Collections.emptyMap());
        }

        // 优先检查 mcpServers 根节点（标准规范与 Claude Desktop 规范）
        JsonNode serversNode = rootNode.get("mcpServers");
        if (serversNode == null || !serversNode.isObject()) {
            // 兼容性降级：若直接平铺或无 mcpServers 包裹
            serversNode = rootNode;
        }

        Map<String, McpServerConfig> serverMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = serversNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String serverId = entry.getKey();
            JsonNode serverDef = entry.getValue();

            // 若节点不是对象，跳过（防格式异常）
            if (serverDef == null || !serverDef.isObject()) {
                continue;
            }

            // 过滤顶层非 Server 键（如常见平铺变体中可能的全局属性）
            if ("$schema".equals(serverId) || "version".equals(serverId)) {
                continue;
            }

            McpServerConfig serverConfig = parseServerConfig(serverId, serverDef);
            serverMap.put(serverId, serverConfig);
        }

        return new McpConfig(serverMap);
    }

    /**
     * 解析单个 Server 节点定义。
     */
    private McpServerConfig parseServerConfig(String serverId, JsonNode serverDef) {
        String command = serverDef.hasNonNull("command") ? serverDef.get("command").asText() : "";

        // 解析 args 列表
        List<String> argsList = new ArrayList<>();
        JsonNode argsNode = serverDef.get("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode arg : argsNode) {
                if (arg != null && !arg.isNull()) {
                    argsList.add(arg.asText());
                }
            }
        }

        // 解析 env 环境变量表
        Map<String, String> envMap = new LinkedHashMap<>();
        JsonNode envNode = serverDef.get("env");
        if (envNode != null && envNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> envFields = envNode.fields();
            while (envFields.hasNext()) {
                Map.Entry<String, JsonNode> envEntry = envFields.next();
                String val = envEntry.getValue() != null && !envEntry.getValue().isNull() ? envEntry.getValue().asText() : "";
                envMap.put(envEntry.getKey(), val);
            }
        }

        // 解析内嵌声明的 tools
        List<GovernedTool> declaredTools = new ArrayList<>();
        JsonNode toolsNode = serverDef.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                if (toolNode != null && toolNode.isObject()) {
                    GovernedTool tool = parseToolNode(toolNode);
                    if (tool != null) {
                        declaredTools.add(tool);
                    }
                }
            }
        }

        return new McpServerConfig(serverId, command, argsList, envMap, declaredTools);
    }

    /**
     * 解析单个内嵌 Tool 节点定义。
     */
    private GovernedTool parseToolNode(JsonNode toolNode) {
        if (!toolNode.hasNonNull("name")) {
            return null;
        }
        String name = toolNode.get("name").asText();
        String description = toolNode.hasNonNull("description") ? toolNode.get("description").asText() : "";

        Map<String, Object> inputSchema = Collections.emptyMap();
        JsonNode schemaNode = toolNode.get("inputSchema");
        if (schemaNode != null && schemaNode.isObject()) {
            try {
                inputSchema = objectMapper.convertValue(schemaNode, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("解析工具 [{}] 的 inputSchema 失败: {}", name, e.getMessage());
                inputSchema = Collections.emptyMap();
            }
        }

        return GovernedTool.of(name, description, inputSchema);
    }
}

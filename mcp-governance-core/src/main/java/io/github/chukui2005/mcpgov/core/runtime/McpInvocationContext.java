package io.github.chukui2005.mcpgov.core.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 运行时 MCP 工具调用上下文（不可变领域模型，防篡改）。
 *
 * @param toolName 工具名称，非空
 * @param arguments 工具调用入参，只读视图
 * @param attributes 会话元数据属性，只读视图
 * @param timestamp 进入治理链的时间戳，非空
 */
public record McpInvocationContext(
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> attributes,
        Instant timestamp
) {
    /**
     * 规范构造函数，强制不可变视图并防止空引用。
     */
    public McpInvocationContext {
        Objects.requireNonNull(toolName, "toolName 不能为 null");
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(arguments));
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(attributes));
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    /**
     * 替换调用参数并返回新的不可变上下文。
     *
     * @param newArgs 新的入参 Map
     * @return 新的不可变调用上下文
     */
    public McpInvocationContext withArguments(Map<String, Object> newArgs) {
        return new McpInvocationContext(this.toolName, newArgs, this.attributes, this.timestamp);
    }

    /**
     * 增加或修改属性并返回新的不可变上下文。
     *
     * @param key 属性键
     * @param value 属性值
     * @return 新的不可变调用上下文
     */
    public McpInvocationContext withAttribute(String key, Object value) {
        Map<String, Object> newAttr = new HashMap<>(this.attributes);
        newAttr.put(key, value);
        return new McpInvocationContext(this.toolName, this.arguments, newAttr, this.timestamp);
    }
}

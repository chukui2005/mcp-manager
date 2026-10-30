package io.github.chukui2005.mcpgovernance.gateway.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 下游 MCP Server 协议类型枚举。
 * 支持 sse、http (Streamable HTTP) 以及 stdio (本地受控托管子进程)。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public enum UpstreamProtocol {

    /**
     * Server-Sent Events 流式协议。
     */
    SSE("sse"),

    /**
     * Streamable HTTP JSON-RPC 协议。
     */
    HTTP("http"),

    /**
     * 本地受控托管 STDIO 管道子进程协议。
     */
    STDIO("stdio");

    private final String value;

    UpstreamProtocol(String value) {
        this.value = value;
    }

    /**
     * 获取协议的底层序列化值。
     *
     * @return 协议标识字符串
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 从字符串反序列化协议枚举。
     *
     * @param value 字符串值
     * @return 对应的协议枚举
     * @throws IllegalArgumentException 当值不受支持时抛出
     */
    @JsonCreator
    public static UpstreamProtocol fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (UpstreamProtocol protocol : values()) {
            if (protocol.value.equals(normalized)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("不支持的下游协议类型: " + value);
    }
}

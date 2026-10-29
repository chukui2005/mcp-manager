package io.github.chukui2005.mcpgov.core.runtime;

/**
 * MCP 治理基础运行时异常。
 */
public class McpGovernanceException extends RuntimeException {

    /**
     * 构造治理异常。
     *
     * @param message 异常信息
     */
    public McpGovernanceException(String message) {
        super(message);
    }

    /**
     * 构造包含底层根因的治理异常。
     *
     * @param message 异常信息
     * @param cause 底层异常
     */
    public McpGovernanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

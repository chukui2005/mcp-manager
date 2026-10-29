package io.github.chukui2005.mcpgov.core.runtime;

/**
 * MCP 访问控制与安全策略阻断异常。
 * 当请求违背治理安全规则（如未授权、高危操作限制等）时抛出。
 */
public class McpAccessDeniedException extends McpGovernanceException {

    /**
     * 构造访问拒绝异常。
     *
     * @param message 阻断原因说明
     */
    public McpAccessDeniedException(String message) {
        super(message);
    }

    /**
     * 构造带根因的访问拒绝异常。
     *
     * @param message 阻断原因说明
     * @param cause 底层异常
     */
    public McpAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}

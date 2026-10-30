package io.github.chukui2005.mcpgov.core.runtime.metrics;

/**
 * MCP 工具调用治理结果状态枚举。
 */
public enum InvocationStatus {
    /** 调用成功完成。 */
    SUCCESS,
    /** 因命中 Tool ACL 权限策略被阻断（OWASP MCP02）。 */
    ACCESS_DENIED,
    /** 因未通过入参安全校验被阻断（OWASP MCP05）。 */
    VALIDATION_FAILED,
    /** 工具内部执行异常或系统未捕获错误。 */
    ERROR
}

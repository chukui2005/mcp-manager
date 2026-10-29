package io.github.chukui2005.mcpgov.core.model;

/**
 * OWASP MCP Top 10 安全分类枚举。
 * 覆盖 MCP01 至 MCP10 安全威胁基线。
 */
public enum OwaspCategory {

    /**
     * MCP01: Token 管理不当与密钥暴露 (Improper Token Management)
     */
    MCP01("Token 管理不当与密钥暴露"),

    /**
     * MCP02: 权限范围蔓延与过度授权 (Scope Creep and Over-privileged Access)
     */
    MCP02("权限范围蔓延与过度授权"),

    /**
     * MCP03: 工具投毒与动态篡改 (Tool Poisoning and Rug-pulling)
     */
    MCP03("工具投毒与动态篡改"),

    /**
     * MCP04: 供应链攻击与不可信来源 (Supply Chain Vulnerabilities)
     */
    MCP04("供应链攻击与不可信来源"),

    /**
     * MCP05: 命令注入与未授权代码执行 (Command Injection and Untrusted Execution)
     */
    MCP05("命令注入与未授权代码执行"),

    /**
     * MCP06: 上下文载荷中的间接提示注入 (Indirect Prompt Injection via Payloads)
     */
    MCP06("上下文载荷中的间接提示注入"),

    /**
     * MCP07: 认证与授权不足 (Insufficient Auth and Authz)
     */
    MCP07("认证与授权不足"),

    /**
     * MCP08: 审计与遥测缺失 (Lack of Audit Trail and Telemetry)
     */
    MCP08("审计与遥测缺失"),

    /**
     * MCP09: 影子 MCP Server (Shadow MCP Servers)
     */
    MCP09("影子 MCP Server"),

    /**
     * MCP10: 上下文过度共享 (Excessive Context Sharing)
     */
    MCP10("上下文过度共享");

    private final String description;

    OwaspCategory(String description) {
        this.description = description;
    }

    /**
     * 获取分类中文描述
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}

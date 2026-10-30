package io.github.chukui2005.mcpgov.core.runtime.policy;

import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tool ACL 访问控制拦截器单元测试。
 * 验证白名单放行、白名单默认拒绝、黑名单拦截、通配符匹配及未启用等场景。
 */
class ToolAclMcpInterceptorTest {

    private McpInvocationContext createContext(String toolName) {
        return new McpInvocationContext(toolName, Map.of(), Map.of(), Instant.now());
    }

    @Test
    @DisplayName("测试场景1：白名单模式下，在允许列表中的工具正常放行且上下文不被篡改")
    void testWhitelistAllowed() {
        ToolAclPolicy policy = new ToolAclPolicy(
            true,
            AclMode.WHITELIST,
            List.of("calculator", "file_read"),
            List.of()
        );
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        McpInvocationContext context = createContext("calculator");
        McpInvocationContext result = interceptor.preHandle(context);

        assertNotNull(result);
        assertEquals("calculator", result.toolName());
    }

    @Test
    @DisplayName("测试场景2：白名单模式下，未在允许列表中的工具默认拒绝并抛出 McpAccessDeniedException")
    void testWhitelistDefaultDeny() {
        ToolAclPolicy policy = new ToolAclPolicy(
            true,
            AclMode.WHITELIST,
            List.of("calculator"),
            List.of()
        );
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        McpInvocationContext context = createContext("system_exec");
        McpAccessDeniedException ex = assertThrows(McpAccessDeniedException.class, () -> interceptor.preHandle(context));
        assertTrue(ex.getMessage().contains("OWASP MCP02"));
        assertTrue(ex.getMessage().contains("system_exec"));
    }

    @Test
    @DisplayName("测试场景3：黑名单模式下，命中黑名单规则的工具抛出拒绝异常，其余放行")
    void testBlacklistBlocked() {
        ToolAclPolicy policy = new ToolAclPolicy(
            true,
            AclMode.BLACKLIST,
            List.of(),
            List.of("system_exec", "bash")
        );
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        // 黑名单内的工具被拦截
        McpInvocationContext blockedContext = createContext("system_exec");
        assertThrows(McpAccessDeniedException.class, () -> interceptor.preHandle(blockedContext));

        // 黑名单外的工具默认放行
        McpInvocationContext allowedContext = createContext("calculator");
        assertDoesNotThrow(() -> interceptor.preHandle(allowedContext));
    }

    @Test
    @DisplayName("测试场景4：通配符模式匹配 (如 file_*、db:delete*)")
    void testWildcardPatternMatching() {
        ToolAclPolicy policy = new ToolAclPolicy(
            true,
            AclMode.WHITELIST,
            List.of("file_*", "db:read:*"),
            List.of("file_delete*")
        );
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        // 匹配前缀白名单
        assertDoesNotThrow(() -> interceptor.preHandle(createContext("file_read")));
        assertDoesNotThrow(() -> interceptor.preHandle(createContext("db:read:users")));

        // 黑名单优先于白名单：file_delete_all 同时匹配白名单 file_* 与黑名单 file_delete*，黑名单优先拒绝
        assertThrows(McpAccessDeniedException.class, () -> interceptor.preHandle(createContext("file_delete_all")));

        // 未命中任何白名单
        assertThrows(McpAccessDeniedException.class, () -> interceptor.preHandle(createContext("system_run")));
    }

    @Test
    @DisplayName("测试策略未启用状态下全部放行")
    void testPolicyDisabled() {
        ToolAclPolicy policy = new ToolAclPolicy(
            false,
            AclMode.WHITELIST,
            List.of("calculator"),
            List.of("system_exec")
        );
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        assertDoesNotThrow(() -> interceptor.preHandle(createContext("system_exec")));
        assertDoesNotThrow(() -> interceptor.preHandle(createContext("unregistered_tool")));
    }

    @Test
    @DisplayName("测试空工具名称阻断与优先级排序")
    void testEmptyToolNameAndOrder() {
        ToolAclPolicy policy = new ToolAclPolicy(true, AclMode.WHITELIST, List.of("*"), List.of());
        ToolAclMcpInterceptor interceptor = new ToolAclMcpInterceptor(policy);

        assertEquals(100, interceptor.getOrder());
        assertFalse(policy.isAllowed(""));
        assertFalse(policy.isAllowed("   "));
        assertFalse(policy.isAllowed(null));
    }
}

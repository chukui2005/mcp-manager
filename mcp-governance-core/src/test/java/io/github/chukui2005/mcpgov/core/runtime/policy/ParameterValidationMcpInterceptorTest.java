package io.github.chukui2005.mcpgov.core.runtime.policy;

import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数校验运行时拦截器单元测试。
 * 验证必填字段校验、路径遍历防范正则、正向格式校验以及规则作用范围。
 */
class ParameterValidationMcpInterceptorTest {

    private McpInvocationContext createContext(String toolName, Map<String, Object> arguments) {
        return new McpInvocationContext(toolName, arguments, Map.of(), Instant.now());
    }

    @Test
    @DisplayName("测试场景5：缺少必填参数时拦截并抛出治理异常")
    void testRequiredParameterMissing() {
        ParameterValidationRule rule = new ParameterValidationRule(
            "file_*",
            "path",
            true,
            null,
            null,
            "文件路径参数 path 必须提供"
        );
        ParameterValidationMcpInterceptor interceptor = new ParameterValidationMcpInterceptor(List.of(rule));

        // 缺少必填参数 path
        McpInvocationContext contextMissing = createContext("file_read", Map.of("offset", 0));
        McpGovernanceException ex = assertThrows(McpGovernanceException.class, () -> interceptor.preHandle(contextMissing));
        assertTrue(ex.getMessage().contains("文件路径参数 path 必须提供"));

        // 空字符串也视为未提供
        McpInvocationContext contextBlank = createContext("file_read", Map.of("path", "   "));
        assertThrows(McpGovernanceException.class, () -> interceptor.preHandle(contextBlank));

        // 提供了合法参数
        McpInvocationContext contextValid = createContext("file_read", Map.of("path", "/var/log/app.log"));
        assertDoesNotThrow(() -> interceptor.preHandle(contextValid));
    }

    @Test
    @DisplayName("测试场景6：参数包含路径遍历字符命中 disallowedRegex 抛出阻断异常")
    void testDisallowedRegexTraversal() {
        ParameterValidationRule rule = new ParameterValidationRule(
            "file_*",
            "path",
            true,
            ".*(\\.\\./|~).*",
            null,
            "禁止路径遍历攻击 (MCP05)"
        );
        ParameterValidationMcpInterceptor interceptor = new ParameterValidationMcpInterceptor(List.of(rule));

        // 尝试通过相对路径向上遍历
        McpInvocationContext maliciousContext = createContext("file_read", Map.of("path", "../../../etc/shadow"));
        McpGovernanceException ex = assertThrows(McpGovernanceException.class, () -> interceptor.preHandle(maliciousContext));
        assertTrue(ex.getMessage().contains("禁止路径遍历攻击 (MCP05)"));

        // 正常绝对路径合规通过
        McpInvocationContext safeContext = createContext("file_read", Map.of("path", "/data/safe/file.txt"));
        assertDoesNotThrow(() -> interceptor.preHandle(safeContext));
    }

    @Test
    @DisplayName("测试必需满足格式 requiredRegex 校验")
    void testRequiredRegexFormat() {
        ParameterValidationRule rule = new ParameterValidationRule(
            "sql_query",
            "timeoutSec",
            false,
            null,
            "^\\d+$",
            "timeoutSec 必须为纯数字"
        );
        ParameterValidationMcpInterceptor interceptor = new ParameterValidationMcpInterceptor(List.of(rule));

        // 非法字符串
        McpInvocationContext invalidContext = createContext("sql_query", Map.of("timeoutSec", "10s"));
        assertThrows(McpGovernanceException.class, () -> interceptor.preHandle(invalidContext));

        // 合法纯数字
        McpInvocationContext validContext = createContext("sql_query", Map.of("timeoutSec", "30"));
        assertDoesNotThrow(() -> interceptor.preHandle(validContext));
    }

    @Test
    @DisplayName("测试非目标工具不触发参数校验")
    void testNonMatchingToolSkipped() {
        ParameterValidationRule rule = new ParameterValidationRule(
            "db_*",
            "connectionString",
            true,
            null,
            null,
            "数据库连接串必填"
        );
        ParameterValidationMcpInterceptor interceptor = new ParameterValidationMcpInterceptor(List.of(rule));

        // 其他工具不包含 connectionString 也不会被拦截
        McpInvocationContext otherContext = createContext("file_read", Map.of("path", "/test"));
        assertDoesNotThrow(() -> interceptor.preHandle(otherContext));
    }

    @Test
    @DisplayName("测试拦截器排序为 200")
    void testOrder() {
        ParameterValidationMcpInterceptor interceptor = new ParameterValidationMcpInterceptor(List.of());
        assertEquals(200, interceptor.getOrder());
    }
}

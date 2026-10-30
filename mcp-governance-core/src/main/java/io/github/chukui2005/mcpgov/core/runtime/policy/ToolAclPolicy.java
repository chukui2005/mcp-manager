package io.github.chukui2005.mcpgov.core.runtime.policy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tool ACL 访问控制策略定义（纯 Java 17，无外部依赖）。
 * 用于治理 OWASP MCP02 权限蔓延风险。
 *
 * @param enabled 是否启用策略控制
 * @param defaultMode 默认模式（WHITELIST / BLACKLIST）
 * @param allowedTools 允许调用的工具名称列表或通配符规则
 * @param blockedTools 拒绝调用的工具名称列表或通配符规则
 */
public record ToolAclPolicy(
    boolean enabled,
    AclMode defaultMode,
    List<String> allowedTools,
    List<String> blockedTools
) {

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 规范构造函数，处理默认值并进行不可变副本封装。
     */
    public ToolAclPolicy {
        defaultMode = defaultMode == null ? AclMode.WHITELIST : defaultMode;
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        blockedTools = blockedTools == null ? List.of() : List.copyOf(blockedTools);
    }

    /**
     * 判断指定工具是否被当前策略允许执行。
     *
     * @param toolName 待检查的工具名称
     * @return true 表示允许执行；false 表示拒绝执行
     */
    public boolean isAllowed(String toolName) {
        if (!enabled) {
            return true;
        }
        if (toolName == null || toolName.isBlank()) {
            return false;
        }

        // 1. 黑名单优先检查：若命中任何 blockedPattern，立即拒绝
        for (String pattern : blockedTools) {
            if (matchesPattern(toolName, pattern)) {
                return false;
            }
        }

        // 2. 根据模式决定白名单检查
        if (defaultMode == AclMode.BLACKLIST) {
            return true;
        }

        // 3. WHITELIST 模式：必须至少命中一个 allowedPattern
        for (String pattern : allowedTools) {
            if (matchesPattern(toolName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String text, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (!pattern.contains("*")) {
            return text.equals(pattern);
        }
        Pattern regexPattern = PATTERN_CACHE.computeIfAbsent(pattern, p -> {
            String regex = "\\Q" + p.replace("*", "\\E.*\\Q") + "\\E";
            return Pattern.compile(regex);
        });
        return regexPattern.matcher(text).matches();
    }
}

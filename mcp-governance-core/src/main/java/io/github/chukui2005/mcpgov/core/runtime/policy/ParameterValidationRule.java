package io.github.chukui2005.mcpgov.core.runtime.policy;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 工具入参参数校验规则定义（纯 Java 17，无外部依赖）。
 * 用于防范恶意入参注入与路径遍历等安全风险。
 *
 * @param toolPattern 适用的工具名称或通配符规则（默认为 "*"）
 * @param parameterName 校验的目标参数名
 * @param required 是否必填
 * @param disallowedRegex 禁用取值的正则表达式（命中则阻断）
 * @param requiredRegex 必须符合的正则表达式（未命中则阻断）
 * @param errorMessage 校验失败时的错误提示信息
 */
public record ParameterValidationRule(
    String toolPattern,
    String parameterName,
    boolean required,
    String disallowedRegex,
    String requiredRegex,
    String errorMessage
) {

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 规范构造函数，处理默认值与非空检查。
     */
    public ParameterValidationRule {
        toolPattern = (toolPattern == null || toolPattern.isBlank()) ? "*" : toolPattern;
        Objects.requireNonNull(parameterName, "parameterName 不能为 null");
    }

    /**
     * 判断当前校验规则是否适用于指定的工具名称。
     *
     * @param toolName 工具名称
     * @return true 表示适用；false 表示不适用
     */
    public boolean appliesTo(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        if ("*".equals(toolPattern)) {
            return true;
        }
        if (!toolPattern.contains("*")) {
            return toolName.equals(toolPattern);
        }
        Pattern regexPattern = PATTERN_CACHE.computeIfAbsent(toolPattern, p -> {
            String regex = "\\Q" + p.replace("*", "\\E.*\\Q") + "\\E";
            return Pattern.compile(regex);
        });
        return regexPattern.matcher(toolName).matches();
    }
}

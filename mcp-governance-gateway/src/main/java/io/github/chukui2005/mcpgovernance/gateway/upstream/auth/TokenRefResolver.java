package io.github.chukui2005.mcpgovernance.gateway.upstream.auth;

import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 下游凭证与环境变量引用解析器。
 * 遵循《AGENTS.md》安全基线与 OWASP MCP01，负责将 tokenRef 安全解析为系统环境变量值，
 * 严禁明文硬编码 Secret，严禁在日志中打印 Secret 实际值。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class TokenRefResolver {

    private static final Logger log = LoggerFactory.getLogger(TokenRefResolver.class);

    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{([a-zA-Z0-9_]+)\\}$");
    private static final Pattern PLAIN_ENV_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * 默认构造函数。
     */
    public TokenRefResolver() {
    }

    /**
     * 解析环境变量引用名称。
     * 支持形如 "${VAR_NAME}" 或直接为 "VAR_NAME" 的格式。
     *
     * @param tokenRef token 引用表达式
     * @return 实际解析出的环境变量名称，若格式非法或为空则返回 null
     */
    public String extractEnvVariableName(String tokenRef) {
        if (tokenRef == null || tokenRef.trim().isEmpty()) {
            return null;
        }
        String trimmed = tokenRef.trim();
        Matcher matcher = ENV_PLACEHOLDER_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        // 直接为变量名（只包含字母数字下划线）
        if (PLAIN_ENV_NAME_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        log.warn("无法解析的环境变量引用表达式格式: {}", tokenRef);
        return null;
    }

    /**
     * 根据环境变量引用解析实际的凭证值。
     * 从系统环境变量 (System.getenv) 中获取。
     *
     * @param tokenRef token 引用表达式
     * @return 环境变量对应的值，若未配置或未找到则返回 null
     */
    public String resolveToken(String tokenRef) {
        String envVarName = extractEnvVariableName(tokenRef);
        if (envVarName == null) {
            return null;
        }
        String value = System.getenv(envVarName);
        if (value == null || value.trim().isEmpty()) {
            log.debug("未检测到环境变量 [{}] 的取值", envVarName);
            return null;
        }
        return value.trim();
    }

    /**
     * 计算并构造出站请求头的值。
     * 针对 bearer 类型拼接 "Bearer <token>"；apiKey 和 basic 则直接采用 token 值。
     *
     * @param authConfig 下游认证配置
     * @return 最终的请求头完整值，若无配置或无法解析出有效 token 则返回 null
     */
    public String buildHeaderValue(UpstreamAuthConfig authConfig) {
        if (authConfig == null || authConfig.getTokenRef() == null) {
            return null;
        }
        String token = resolveToken(authConfig.getTokenRef());
        if (token == null || token.isEmpty()) {
            return null;
        }

        String type = authConfig.getType();
        if ("bearer".equalsIgnoreCase(type)) {
            return "Bearer " + token;
        } else if ("basic".equalsIgnoreCase(type)) {
            // basic 若直接是 base64 或 raw 则带 Basic 前缀
            return token.toLowerCase().startsWith("basic ") ? token : "Basic " + token;
        } else {
            // apiKey 或其他自定义类型直接传递 token
            return token;
        }
    }
}

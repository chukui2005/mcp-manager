package io.github.chukui2005.mcpgovernance.gateway.upstream.auth;

import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下游凭据环境变量解析器 {@link TokenRefResolver} 单元测试。
 *
 * @author chukui2005
 * @since 0.1.0
 */
class TokenRefResolverTest {

    private final TokenRefResolver resolver = new TokenRefResolver();

    @Test
    @DisplayName("测试提取环境变量名（支持 ${VAR} 与裸 VAR）")
    void testExtractEnvVariableName() {
        assertThat(resolver.extractEnvVariableName("${GITHUB_TOKEN}")).isEqualTo("GITHUB_TOKEN");
        assertThat(resolver.extractEnvVariableName("GITHUB_TOKEN")).isEqualTo("GITHUB_TOKEN");
        assertThat(resolver.extractEnvVariableName("  ${MY_VAR_123}  ")).isEqualTo("MY_VAR_123");
        assertThat(resolver.extractEnvVariableName(null)).isNull();
        assertThat(resolver.extractEnvVariableName("")).isNull();
        assertThat(resolver.extractEnvVariableName("   ")).isNull();
        assertThat(resolver.extractEnvVariableName("invalid-format@!#")).isNull();
    }

    @Test
    @DisplayName("测试解析实际环境变量（使用通用系统变量如 PATH 或 SystemRoot）")
    void testResolveTokenWithSystemEnv() {
        // 大多数系统存在 PATH 环境变量
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null && !pathEnv.trim().isEmpty()) {
            String resolved = resolver.resolveToken("${PATH}");
            assertThat(resolved).isEqualTo(pathEnv.trim());
        }

        // 测试不存在的环境变量
        String notFound = resolver.resolveToken("${NON_EXISTENT_TOKEN_REF_XYZ}");
        assertThat(notFound).isNull();
    }

    @Test
    @DisplayName("测试根据 AuthConfig 生成 Header 值")
    void testBuildHeaderValue() {
        UpstreamAuthConfig bearerConfig = new UpstreamAuthConfig();
        bearerConfig.setType("bearer");
        bearerConfig.setTokenRef("${PATH}"); // 复用系统已存在的 PATH 变量测试

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null && !pathEnv.trim().isEmpty()) {
            String header = resolver.buildHeaderValue(bearerConfig);
            assertThat(header).isEqualTo("Bearer " + pathEnv.trim());
        }

        UpstreamAuthConfig basicConfig = new UpstreamAuthConfig();
        basicConfig.setType("basic");
        basicConfig.setTokenRef("${PATH}");
        if (pathEnv != null && !pathEnv.trim().isEmpty()) {
            String header = resolver.buildHeaderValue(basicConfig);
            assertThat(header).startsWith("Basic ");
        }

        UpstreamAuthConfig apiKeyConfig = new UpstreamAuthConfig();
        apiKeyConfig.setType("apiKey");
        apiKeyConfig.setTokenRef("${PATH}");
        if (pathEnv != null && !pathEnv.trim().isEmpty()) {
            String header = resolver.buildHeaderValue(apiKeyConfig);
            assertThat(header).isEqualTo(pathEnv.trim());
        }

        // 测试空配置或无效 tokenRef
        assertThat(resolver.buildHeaderValue(null)).isNull();

        UpstreamAuthConfig emptyConfig = new UpstreamAuthConfig();
        assertThat(resolver.buildHeaderValue(emptyConfig)).isNull();
    }
}

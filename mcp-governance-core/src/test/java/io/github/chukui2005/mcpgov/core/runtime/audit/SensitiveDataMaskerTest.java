package io.github.chukui2005.mcpgov.core.runtime.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveDataMasker 敏感参数递归脱敏测试")
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("空值与空 Map 应安全返回空 Map")
    void shouldHandleNullAndEmpty() {
        assertThat(SensitiveDataMasker.mask(null)).isEmpty();
        assertThat(SensitiveDataMasker.mask(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("常见敏感关键词识别验证（不区分大小写）")
    void shouldRecognizeSensitiveKeys() {
        assertThat(SensitiveDataMasker.isSensitiveKey("password")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("User_Password")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("secretKey")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("mcp_token")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("apiKey")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("API_KEY")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("Authorization")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("privateKey")).isTrue();
        assertThat(SensitiveDataMasker.isSensitiveKey("rsa_private_key")).isTrue();

        assertThat(SensitiveDataMasker.isSensitiveKey("username")).isFalse();
        assertThat(SensitiveDataMasker.isSensitiveKey("toolName")).isFalse();
        assertThat(SensitiveDataMasker.isSensitiveKey(null)).isFalse();
        assertThat(SensitiveDataMasker.isSensitiveKey("")).isFalse();
    }

    @Test
    @DisplayName("平铺参数脱敏：敏感词打码，非敏感词保留")
    void shouldMaskFlatArguments() {
        Map<String, Object> input = Map.of(
                "username", "admin",
                "password", "p@ssw0rd123",
                "token", "eyJhbGciOi...",
                "limit", 50
        );

        Map<String, Object> masked = SensitiveDataMasker.mask(input);

        assertThat(masked)
                .containsEntry("username", "admin")
                .containsEntry("password", "******")
                .containsEntry("token", "******")
                .containsEntry("limit", 50);
    }

    @Test
    @DisplayName("多层嵌套 Map 与 List 递归脱敏测试")
    void shouldMaskNestedStructuresRecursively() {
        Map<String, Object> nestedHeaders = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer sk-1234567890"
        );

        Map<String, Object> credentials = Map.of(
                "apiKey", "super-secret-key",
                "vendor", "openai"
        );

        Map<String, Object> input = Map.of(
                "service", "chat",
                "headers", nestedHeaders,
                "items", List.of(credentials, Map.of("id", 1, "secret", "dont-leak-me"))
        );

        Map<String, Object> masked = SensitiveDataMasker.mask(input);

        assertThat(masked.get("service")).isEqualTo("chat");

        @SuppressWarnings("unchecked")
        Map<String, Object> maskedHeaders = (Map<String, Object>) masked.get("headers");
        assertThat(maskedHeaders.get("Content-Type")).isEqualTo("application/json");
        assertThat(maskedHeaders.get("Authorization")).isEqualTo("******");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maskedItems = (List<Map<String, Object>>) masked.get("items");
        assertThat(maskedItems).hasSize(2);
        assertThat(maskedItems.get(0).get("apiKey")).isEqualTo("******");
        assertThat(maskedItems.get(0).get("vendor")).isEqualTo("openai");
        assertThat(maskedItems.get(1).get("id")).isEqualTo(1);
        assertThat(maskedItems.get(1).get("secret")).isEqualTo("******");
    }
}

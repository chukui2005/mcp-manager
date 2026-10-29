package io.github.chukui2005.mcpgov.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 治理层受控工具实体 (Governed Tool)。
 * <p>
 * 遵循控制面模型与 Wire 传输模型强隔离原则：
 * 1. 强制持有强类型的规范化元数据（名称、描述）；
 * 2. 强制持有 JSON Schema 定义（不得为 null）；
 * 3. 构造期基于规范化 JSON 表达自动计算 SHA-256 完整性指纹，防范 MCP03 工具投毒与篡改；
 * 4. 具备完全不可变性 (Immutable Record)。
 * </p>
 *
 * @param name 工具唯一名称 (非空)
 * @param description 工具语义描述 (不可为 null，若外部未提供则默认为空字符串)
 * @param inputSchema 入参 JSON Schema 结构 (非空)
 * @param sha256 结构与语义 SHA-256 指纹 (64 位小写十六进制)
 * @param riskTags 关联的风险标签集 (非空，如 READ_ONLY, FILE_WRITE, EXECUTE, NETWORK)
 * @param severity 固有风险等级
 */
public record GovernedTool(
        String name,
        String description,
        Map<String, Object> inputSchema,
        String sha256,
        Set<String> riskTags,
        Severity severity
) {

    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    /**
     * 紧凑构造器，执行非空与不可变规约校验
     */
    public GovernedTool {
        Objects.requireNonNull(name, "工具名称 (name) 不能为空");
        Objects.requireNonNull(inputSchema, "输入模式 (inputSchema) 不能为空");
        description = description != null ? description : "";
        inputSchema = Map.copyOf(inputSchema);
        riskTags = riskTags != null ? Set.copyOf(riskTags) : Collections.emptySet();
        severity = severity != null ? severity : Severity.LOW;

        if (sha256 == null || sha256.isBlank()) {
            sha256 = computeSha256(name, description, inputSchema);
        }
    }

    /**
     * 工厂方法：基于原始元数据构建 GovernedTool 并自动推导 SHA-256
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param inputSchema 入参 Schema
     * @return 受控工具实例
     */
    public static GovernedTool of(String name, String description, Map<String, Object> inputSchema) {
        return new GovernedTool(name, description, inputSchema, null, Collections.emptySet(), Severity.LOW);
    }

    /**
     * 工厂方法：指定风险标签与级别构建
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param inputSchema 入参 Schema
     * @param riskTags 风险标签
     * @param severity 风险等级
     * @return 受控工具实例
     */
    public static GovernedTool of(String name, String description, Map<String, Object> inputSchema, Set<String> riskTags, Severity severity) {
        return new GovernedTool(name, description, inputSchema, null, riskTags, severity);
    }

    /**
     * 计算工具定义的规范化 SHA-256 指纹
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param schema Schema 映射
     * @return 64位十六进制哈希
     */
    public static String computeSha256(String name, String description, Map<String, Object> schema) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonicalSchemaJson = CANONICAL_MAPPER.writeValueAsString(schema);
            String payload = "name:" + name + "|description:" + description + "|schema:" + canonicalSchemaJson;
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("计算 GovernedTool SHA-256 指纹失败", e);
        }
    }
}

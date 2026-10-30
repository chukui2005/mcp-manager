package io.github.chukui2005.mcpgov.core.runtime.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 敏感数据脱敏工具类。
 * 纯 Java 领域安全工具，支持深度递归脱敏嵌套 Map 与 List，
 * 将敏感键名对应的值替换为安全掩码 {@code ******}。
 */
public final class SensitiveDataMasker {

    /**
     * 默认安全掩码字符串。
     */
    public static final String MASK = "******";

    /**
     * 内置敏感字段关键词特征表（不区分大小写）。
     */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "token", "apikey", "api_key", "authorization", "privatekey", "private_key"
    );

    private SensitiveDataMasker() {
        // 工具类禁止实例化
    }

    /**
     * 深度递归脱敏参数 Map。
     *
     * @param source 原始入参 Map，允许为 null
     * @return 脱敏后的不可变 Map
     */
    public static Map<String, Object> mask(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                result.put(key, MASK);
            } else {
                result.put(key, maskValue(value));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 判断某个键名是否包含敏感词。
     *
     * @param key 键名
     * @return 若命中敏感特征词返回 true，否则返回 false
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static Object maskValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String subKey = String.valueOf(entry.getKey());
                Object subVal = entry.getValue();
                if (isSensitiveKey(subKey)) {
                    copy.put(subKey, MASK);
                } else {
                    copy.put(subKey, maskValue(subVal));
                }
            }
            return Collections.unmodifiableMap(copy);
        } else if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(maskValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}

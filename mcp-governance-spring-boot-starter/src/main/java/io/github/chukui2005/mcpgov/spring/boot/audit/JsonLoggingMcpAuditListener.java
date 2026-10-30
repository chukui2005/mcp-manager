package io.github.chukui2005.mcpgov.spring.boot.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 结构化 JSON 格式审计日志监听器。
 * 将 {@link McpAuditEvent} 序列化为规范的单行紧凑 JSON 格式，
 * 并输出至指定的 SLF4J Logger（默认 {@code io.github.chukui2005.mcpgov.audit}），
 * 满足 ELK / Splunk / SIEM 零配置采集索引要求。
 */
public class JsonLoggingMcpAuditListener implements McpAuditListener {

    private static final Logger defaultLog = LoggerFactory.getLogger("io.github.chukui2005.mcpgov.audit");

    private final ObjectMapper objectMapper;
    private final Logger auditLogger;

    /**
     * 默认构造函数，使用内部标准单行 ObjectMapper 与默认 logger。
     */
    public JsonLoggingMcpAuditListener() {
        this(createDefaultObjectMapper(), defaultLog);
    }

    /**
     * 构造函数，支持外部注入 ObjectMapper 与 logger 名称。
     *
     * @param objectMapper Jackson 映射器，允许为 null（为 null 则自动创建默认实例）
     * @param loggerName SLF4J 日志记录器名称，允许为 null（为 null 则使用默认 logger）
     */
    public JsonLoggingMcpAuditListener(ObjectMapper objectMapper, String loggerName) {
        this(
                objectMapper != null ? objectMapper : createDefaultObjectMapper(),
                loggerName != null && !loggerName.isBlank() ? LoggerFactory.getLogger(loggerName) : defaultLog
        );
    }

    /**
     * 全参构造函数。
     *
     * @param objectMapper Jackson 映射器，非空
     * @param auditLogger SLF4J 日志记录器，非空
     */
    public JsonLoggingMcpAuditListener(ObjectMapper objectMapper, Logger auditLogger) {
        ObjectMapper targetMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        ensureJavaTimeModule(targetMapper);
        this.objectMapper = targetMapper;
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger 不能为 null");
    }

    private static void ensureJavaTimeModule(ObjectMapper mapper) {
        try {
            Class<?> timeModuleClass = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            mapper.registerModule((com.fasterxml.jackson.databind.Module) timeModuleClass.getDeclaredConstructor().newInstance());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        } catch (Throwable ignored) {
            // 环境未引入 jackson-datatype-jsr310 时安全忽略
        }
    }

    @Override
    public void onAuditEvent(McpAuditEvent event) {
        if (event == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            auditLogger.info("{}", json);
        } catch (JsonProcessingException e) {
            auditLogger.warn("序列化 MCP 审计事件为 JSON 失败 [{}]: {}", event.eventId(), e.getMessage());
        }
    }

    /**
     * 获取持有的 ObjectMapper。
     *
     * @return 序列化映射器
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 获取持有的日志记录器。
     *
     * @return 日志记录器
     */
    public Logger getAuditLogger() {
        return auditLogger;
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        ensureJavaTimeModule(mapper);
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}

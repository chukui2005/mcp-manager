package io.github.chukui2005.mcpgov.spring.boot.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DisplayName("JsonLoggingMcpAuditListener 结构化 JSON 格式导出测试")
class JsonLoggingMcpAuditListenerTest {

    @Test
    @DisplayName("验证将 McpAuditEvent 准确格式化为单行 JSON 并输出到 Logger")
    void shouldFormatAuditEventAsSingleLineJson() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("test.mcp.audit");
        logger.setLevel(ch.qos.logback.classic.Level.INFO);

        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        ObjectMapper mapper = new ObjectMapper();
        JsonLoggingMcpAuditListener listener = new JsonLoggingMcpAuditListener(mapper, logger);

        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-10-31T12:00:00Z");

        McpAuditEvent event = new McpAuditEvent(
                eventId,
                now,
                "trace-101",
                "agent-alpha",
                "weather_tool",
                Map.of("city", "Beijing", "token", "******"),
                InvocationStatus.SUCCESS,
                42L,
                null,
                Map.of("clientIp", "127.0.0.1")
        );

        listener.onAuditEvent(event);

        assertThat(appender.list).isNotEmpty();
        // 查找 INFO 级别的日志，过滤可能的 WARN
        String json = appender.list.stream()
                .filter(e -> ch.qos.logback.classic.Level.INFO.equals(e.getLevel()))
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 INFO 审计日志"));

        // 验证单行（不包含换行符）
        assertThat(json).doesNotContain("\n").doesNotContain("\r");

        // 验证 JSON 反序列化合法性与契约字段
        JsonNode root = mapper.readTree(json);
        assertThat(root.get("eventId").asText()).isEqualTo(eventId);
        assertThat(root.get("traceId").asText()).isEqualTo("trace-101");
        assertThat(root.get("callerId").asText()).isEqualTo("agent-alpha");
        assertThat(root.get("toolName").asText()).isEqualTo("weather_tool");
        assertThat(root.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(root.get("durationMs").asLong()).isEqualTo(42L);
        assertThat(root.get("arguments").get("city").asText()).isEqualTo("Beijing");
        assertThat(root.get("arguments").get("token").asText()).isEqualTo("******");
        assertThat(root.get("attributes").get("clientIp").asText()).isEqualTo("127.0.0.1");
    }
}

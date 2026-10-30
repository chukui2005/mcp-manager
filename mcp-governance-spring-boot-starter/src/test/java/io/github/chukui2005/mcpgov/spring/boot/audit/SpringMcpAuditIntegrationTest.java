package io.github.chukui2005.mcpgov.spring.boot.audit;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.core.runtime.audit.AuditMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import io.github.chukui2005.mcpgov.spring.boot.adapter.McpGovernanceAdapter;
import io.github.chukui2005.mcpgov.spring.boot.autoconfigure.McpGovernanceAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpringMcpAudit 端到端集成与自动装配测试")
class SpringMcpAuditIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpGovernanceAutoConfiguration.class));

    @Test
    @DisplayName("验证默认自动装配了 AuditMcpInterceptor、JsonLoggingMcpAuditListener 与 SpringMcpAuditPublisher")
    void shouldAutoConfigureAuditComponentsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpAuditPublisher.class);
            assertThat(context).hasSingleBean(SpringMcpAuditPublisher.class);
            assertThat(context).hasSingleBean(JsonLoggingMcpAuditListener.class);
            assertThat(context).hasSingleBean(AuditMcpInterceptor.class);

            // 验证责任链已组装 AuditMcpInterceptor
            McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
            assertThat(chain.getInterceptors())
                    .extracting(McpInterceptor::getOrder)
                    .contains(-50);
        });
    }

    @Test
    @DisplayName("验证端到端调用流经治理适配器并成功触发宿主 @EventListener")
    void shouldTriggerHostEventListenerThroughGovernanceAdapter() {
        contextRunner
                .withPropertyValues("mcp.governance.acl.allowed-tools[0]=calculator")
                .withUserConfiguration(HostAuditListenerConfiguration.class)
                .run(context -> {
                    McpInterceptorChain chain = context.getBean(McpInterceptorChain.class);
                    McpGovernanceAdapter adapter = new McpGovernanceAdapter(chain);
                    AuditCaptureService captureService = context.getBean(AuditCaptureService.class);

                    // 执行一次被治理的工具调用
                    McpInvocationResult result = adapter.executeGoverned(
                            "calculator",
                            Map.of("a", 10, "b", 20, "apiKey", "test-secret-key"),
                            Map.of("callerId", "agent-x"),
                            args -> 30
                    );
                    assertThat(result.success()).isTrue();
                    assertThat(result.result()).isEqualTo(30);

                    // 验证宿主 @EventListener 捕获到了 Spring 审计事件
                    List<McpAuditEvent> captured = captureService.getEvents();
                    assertThat(captured).hasSize(1);
                    McpAuditEvent event = captured.get(0);

                    assertThat(event.toolName()).isEqualTo("calculator");
                    assertThat(event.status()).isEqualTo(InvocationStatus.SUCCESS);
                    assertThat(event.callerId()).isEqualTo("agent-x");
                    // 验证敏感词已脱敏
                    assertThat(event.arguments().get("apiKey")).isEqualTo("******");
                    assertThat(event.arguments().get("a")).isEqualTo(10);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class HostAuditListenerConfiguration {

        @Bean
        public AuditCaptureService auditCaptureService() {
            return new AuditCaptureService();
        }
    }

    static class AuditCaptureService {
        private final List<McpAuditEvent> events = new ArrayList<>();

        @EventListener
        public void onAudit(SpringMcpAuditEvent springEvent) {
            events.add(springEvent.getAuditEvent());
        }

        public List<McpAuditEvent> getEvents() {
            return events;
        }
    }
}

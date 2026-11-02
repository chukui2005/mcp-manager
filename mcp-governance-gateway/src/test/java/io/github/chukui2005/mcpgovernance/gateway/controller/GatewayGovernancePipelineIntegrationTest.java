package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgov.core.runtime.audit.AuditMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.audit.DefaultMcpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import io.github.chukui2005.mcpgovernance.gateway.McpGatewayApplication;
import io.github.chukui2005.mcpgovernance.gateway.adapter.DefaultGatewayExecutionAdapter;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关治理流水线（McpInterceptorChain）端到端集成测试。
 * 验证：
 * 1. Tool ACL 访问控制阻断与审计记录生成；
 * 2. 下游故障真实暴露，杜绝伪造为 SUCCESS；
 * 3. 审计事件包含 CallerId 与脱敏参数。
 *
 * @author chukui2005
 */
@SpringBootTest(classes = McpGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayGovernancePipelineIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteManager routeManager;

    @Autowired
    private DefaultGatewayExecutionAdapter executionAdapter;

    private final java.util.concurrent.CopyOnWriteArrayList<McpAuditEvent> capturedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        routeManager.clear();
        capturedEvents.clear();

        // 为网关的拦截链中的 AuditMcpInterceptor 动态注册捕获器
        if (executionAdapter != null && executionAdapter.getInterceptorChain() != null) {
            for (io.github.chukui2005.mcpgov.core.runtime.McpInterceptor interceptor : executionAdapter.getInterceptorChain().getInterceptors()) {
                if (interceptor instanceof AuditMcpInterceptor auditInterceptor) {
                    auditInterceptor.getPublisher().addListener(capturedEvents::add);
                }
            }
        }

        // 注册一个带 Tool ACL 治理策略的测试路由
        RouteDefinition secureRoute = new RouteDefinition(
                "secure-server",
                UpstreamProtocol.HTTP,
                "sec_",
                "http://127.0.0.1:54321/mcp"
        );
        RouteGovernanceConfig gov = new RouteGovernanceConfig();
        gov.setAclMode("WHITELIST");
        gov.setAllowedTools(List.of("sec_allowed_tool", "allowed_tool"));
        gov.setDeniedTools(List.of("sec_forbidden_tool", "forbidden_tool"));
        secureRoute.setGovernance(gov);

        routeManager.registerRoute(secureRoute);
    }

    @Test
    @DisplayName("测试当工具未被 ACL 授权时直接阻断并返回访问拒绝且生成审计事件")
    void shouldBlockUnauthorizedToolCallViaAcl() {
        Map<String, Object> params = Map.of(
                "name", "sec_forbidden_tool",
                "arguments", Map.of("secretKey", "my-secret-123")
        );
        JsonRpcRequest request = new JsonRpcRequest("call-acl-1", "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo("call-acl-1")
                .jsonPath("$.error.code").isEqualTo(-32003);

        // 验证审计事件记录
        assertThat(capturedEvents).isNotEmpty();
        McpAuditEvent lastEvent = capturedEvents.get(capturedEvents.size() - 1);
        assertThat(lastEvent.status()).isEqualTo(InvocationStatus.ACCESS_DENIED);
        assertThat(lastEvent.callerId()).isEqualTo("mcp-gateway-agent");
        // 验证敏感入参已脱敏
        assertThat(lastEvent.arguments().get("secretKey")).isEqualTo("******");
    }

    @Test
    @DisplayName("测试下游连接失败如实暴露错误，严禁篡改为 SUCCESS")
    void shouldExposeDownstreamFailureWithoutMockSuccess() {
        Map<String, Object> params = Map.of(
                "name", "sec_allowed_tool",
                "arguments", Map.of("query", "test")
        );
        JsonRpcRequest request = new JsonRpcRequest("call-fail-1", "tools/call", params);

        webTestClient.post()
                .uri("/mcp/v1/rpc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jsonrpc").isEqualTo("2.0")
                .jsonPath("$.id").isEqualTo("call-fail-1")
                .jsonPath("$.result.isError").isEqualTo(true)
                .jsonPath("$.result.content[0].text").value(text -> {
                    assertThat(text.toString()).containsAnyOf("下游", "失败", "Connection refused", "拒绝");
                });
    }
}

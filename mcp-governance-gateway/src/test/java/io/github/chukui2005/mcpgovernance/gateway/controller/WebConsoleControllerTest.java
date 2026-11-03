package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.model.Severity;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InvocationStatus;
import io.github.chukui2005.mcpgovernance.gateway.aggregation.ToolAggregator;
import io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebConsoleController 控制面响应式 REST 与 SSE 接口集成测试。
 */
class WebConsoleControllerTest {

    private RouteManager routeManager;
    private ToolAggregator toolAggregator;
    private UpstreamToolProvider toolProvider;
    private InMemoryMcpMetricsCollector metricsCollector;
    private InMemoryAuditRingBuffer auditRingBuffer;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        routeManager = new RouteManager();
        metricsCollector = new InMemoryMcpMetricsCollector();
        auditRingBuffer = new InMemoryAuditRingBuffer(50);

        // 注册测试下游路由
        RouteDefinition route = new RouteDefinition();
        route.setId("demo-service");
        route.setPrefix("demo_");
        route.setProtocol(UpstreamProtocol.HTTP);
        route.setUri("http://127.0.0.1:8081/mcp");
        RouteGovernanceConfig gov = new RouteGovernanceConfig();
        gov.setAclMode("BLACKLIST");
        route.setGovernance(gov);
        routeManager.registerRoute(route);

        // 模拟 UpstreamToolProvider
        toolProvider = r -> Mono.just(List.of(
                GovernedTool.of("read_file", "读取文件", Map.of("type", "object"), Set.of(), Severity.LOW),
                GovernedTool.of("delete_file", "删除文件", Map.of("type", "object"), Set.of("high-risk"), Severity.HIGH)
        ));

        toolAggregator = new ToolAggregator(routeManager, toolProvider);

        WebConsoleController controller = new WebConsoleController(
                routeManager,
                toolAggregator,
                toolProvider,
                metricsCollector,
                auditRingBuffer
        );

        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("测试 GET /api/v1/overview 接口返回契约结构与品牌对齐")
    void testGetOverview() {
        // 模拟录入一次成功指标
        metricsCollector.record("demo_read_file", 15L, InvocationStatus.SUCCESS, null);

        webTestClient.get()
                .uri("/api/v1/overview")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.gateway.status").isEqualTo("UP")
                .jsonPath("$.gateway.brand").isEqualTo("mcp-governance")
                .jsonPath("$.counts.upstreamCount").isEqualTo(1)
                .jsonPath("$.counts.activeUpstreamCount").isEqualTo(1)
                .jsonPath("$.counts.toolCount").isEqualTo(2)
                .jsonPath("$.counts.activeToolCount").isEqualTo(2)
                .jsonPath("$.summary.totalInvocations").isEqualTo(1)
                .jsonPath("$.summary.totalSuccess").isEqualTo(1)
                .jsonPath("$.summary.successRate").isEqualTo(1.0);
    }

    @Test
    @DisplayName("测试 GET /api/v1/tools 与 POST /api/v1/tools/acl 动态策略修改及 Tool Picker 联动")
    void testGetToolsAndDynamicAclUpdate() {
        // 1. 初始查询，两个工具默认均为 ALLOWED，且 enabled 为 true
        webTestClient.get()
                .uri("/api/v1/tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].name").isEqualTo("demo_read_file")
                .jsonPath("$[0].aclStatus").isEqualTo("ALLOWED")
                .jsonPath("$[0].upstreamEnabled").isEqualTo(true)
                .jsonPath("$[0].enabled").isEqualTo(true);

        // 2. 动态下发 enabled: false 禁用 demo_read_file (Tool Picker 取消勾选)
        webTestClient.post()
                .uri("/api/v1/tools/acl")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("toolName", "demo_read_file", "enabled", false))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.toolName").isEqualTo("demo_read_file")
                .jsonPath("$.currentStatus").isEqualTo("DENIED")
                .jsonPath("$.enabled").isEqualTo(false);

        // 3. 再次查询工具列表，demo_read_file 的状态应已变为 DENIED，且 enabled 为 false
        webTestClient.get()
                .uri("/api/v1/tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].aclStatus").isEqualTo("DENIED")
                .jsonPath("$[0].enabled").isEqualTo(false);

        // 4. 重新下发 enabled: true 放行 (Tool Picker 勾选)
        webTestClient.post()
                .uri("/api/v1/tools/acl")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("toolName", "demo_read_file", "enabled", true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.currentStatus").isEqualTo("ALLOWED")
                .jsonPath("$.enabled").isEqualTo(true);
    }

    @Test
    @DisplayName("测试 POST /api/v1/routes/status 热启停下游 Server 路由与大盘/工具联动")
    void testUpdateRouteStatus() {
        // 1. 停用 demo-service 路由
        webTestClient.post()
                .uri("/api/v1/routes/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("routeId", "demo-service", "enabled", false))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.routeId").isEqualTo("demo-service")
                .jsonPath("$.enabled").isEqualTo(false)
                .jsonPath("$.status").isEqualTo("STOPPED");

        // 2. 检查路由列表状态
        webTestClient.get()
                .uri("/api/v1/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].enabled").isEqualTo(false)
                .jsonPath("$[0].status").isEqualTo("STOPPED");

        // 3. 检查大盘中在线数量变为 0，聚合工具数变为 0 (因为 Server 已停用)
        webTestClient.get()
                .uri("/api/v1/overview")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.counts.upstreamCount").isEqualTo(1)
                .jsonPath("$.counts.activeUpstreamCount").isEqualTo(0)
                .jsonPath("$.counts.toolCount").isEqualTo(0);

        // 4. 检查全量工具列表中 upstreamEnabled 与 enabled 均变为 false
        webTestClient.get()
                .uri("/api/v1/tools")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].upstreamEnabled").isEqualTo(false)
                .jsonPath("$[0].enabled").isEqualTo(false);

        // 5. 重新热启用 demo-service 路由
        webTestClient.post()
                .uri("/api/v1/routes/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("routeId", "demo-service", "enabled", true))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("UP");

        // 6. 验证恢复在线
        webTestClient.get()
                .uri("/api/v1/overview")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.counts.activeUpstreamCount").isEqualTo(1)
                .jsonPath("$.counts.toolCount").isEqualTo(2);
    }

    @Test
    @DisplayName("测试 POST /api/v1/tools/acl 参数无效与未匹配路由时的错误响应")
    void testAclUpdateErrors() {
        // 参数为空
        webTestClient.post()
                .uri("/api/v1/tools/acl")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("toolName", "", "action", ""))
                .exchange()
                .expectStatus().isBadRequest();

        // 不存在的工具
        webTestClient.post()
                .uri("/api/v1/tools/acl")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("toolName", "non_existing_tool", "action", "DENY"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("测试 GET /api/v1/audit/events 审计快照接口")
    void testGetAuditEvents() {
        auditRingBuffer.publish(new McpAuditEvent(
                "evt-100",
                Instant.now(),
                null,
                "agent-007",
                "demo_read_file",
                Map.of("path", "/workspace/***"),
                InvocationStatus.SUCCESS,
                12L,
                null,
                Map.of()
        ));

        webTestClient.get()
                .uri("/api/v1/audit/events?limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].eventId").isEqualTo("evt-100")
                .jsonPath("$[0].toolName").isEqualTo("demo_read_file")
                .jsonPath("$[0].callerRole").isEqualTo("agent-007")
                .jsonPath("$[0].status").isEqualTo("SUCCESS")
                .jsonPath("$[0].executionTimeMs").isEqualTo(12);
    }

    @Test
    @DisplayName("测试 GET /api/v1/routes 路由列表接口")
    void testGetRoutes() {
        webTestClient.get()
                .uri("/api/v1/routes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("demo-service")
                .jsonPath("$[0].prefix").isEqualTo("demo_")
                .jsonPath("$[0].protocol").isEqualTo("HTTP");
    }
}

package io.github.chukui2005.mcpgovernance.gateway.controller;

import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditEvent;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsSnapshot;
import io.github.chukui2005.mcpgovernance.gateway.aggregation.ToolAggregator;
import io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteMatchResult;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 嵌入式轻量 Web 控制台控制面 REST 与 SSE 接口控制器。
 * 遵循《AGENTS.md》、ADR 0011 与 OpenAPI 契约 docs/contracts/web-console-api.json。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1")
public class WebConsoleController {

    private static final Logger log = LoggerFactory.getLogger(WebConsoleController.class);

    private final RouteManager routeManager;
    private final ToolAggregator toolAggregator;
    private final UpstreamToolProvider upstreamToolProvider;
    private final McpMetricsCollector metricsCollector;
    private final InMemoryAuditRingBuffer auditRingBuffer;
    private final Instant startupTime = Instant.now();

    /**
     * 控制面控制器依赖注入构造函数。
     *
     * @param routeManager         路由管理器
     * @param toolAggregator       工具聚合器
     * @param upstreamToolProvider 下游工具提供器
     * @param metricsCollector     指标度量收集器
     * @param auditRingBuffer      审计事件环形缓冲与 SSE 广播通道
     */
    @Autowired
    public WebConsoleController(
            RouteManager routeManager,
            ToolAggregator toolAggregator,
            UpstreamToolProvider upstreamToolProvider,
            McpMetricsCollector metricsCollector,
            InMemoryAuditRingBuffer auditRingBuffer) {
        this.routeManager = Objects.requireNonNull(routeManager, "routeManager 不能为 null");
        this.toolAggregator = Objects.requireNonNull(toolAggregator, "toolAggregator 不能为 null");
        this.upstreamToolProvider = Objects.requireNonNull(upstreamToolProvider, "upstreamToolProvider 不能为 null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector 不能为 null");
        this.auditRingBuffer = Objects.requireNonNull(auditRingBuffer, "auditRingBuffer 不能为 null");
    }

    /**
     * 获取网关全局大盘数据与运行状态摘要。
     * 对应契约：GET /api/v1/overview
     *
     * @return 大盘概览数据
     */
    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverview() {
        return toolAggregator.aggregateTools().map(tools -> {
            McpMetricsSnapshot snapshot = metricsCollector.getSnapshot();
            Map<String, Object> resp = new LinkedHashMap<>();

            // 1. 网关运行信息
            Map<String, Object> gateway = new LinkedHashMap<>();
            gateway.put("status", "UP");
            gateway.put("version", "0.1.0-SNAPSHOT");
            gateway.put("brand", "mcp-governance");
            gateway.put("uptimeMs", Math.max(0L, Duration.between(startupTime, Instant.now()).toMillis()));
            resp.put("gateway", gateway);

            // 2. 数量统计
            Map<String, Object> counts = new LinkedHashMap<>();
            int totalUpstreams = routeManager.size();
            int activeUpstreams = routeManager.activeSize();
            counts.put("upstreamCount", totalUpstreams);
            counts.put("activeUpstreamCount", activeUpstreams);
            counts.put("toolCount", tools.size());
            counts.put("activeToolCount", tools.size());
            resp.put("counts", counts);

            // 3. 运行时治理度量统计
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalInvocations", snapshot.totalInvocations());
            summary.put("totalSuccess", snapshot.totalSuccess());
            summary.put("totalAccessDenied", snapshot.totalAccessDenied());
            summary.put("totalValidationFailed", snapshot.totalValidationFailed());
            summary.put("totalError", snapshot.totalError());
            summary.put("successRate", snapshot.overallSuccessRate());
            summary.put("deniedRate", snapshot.overallDeniedRate());
            resp.put("summary", summary);

            return resp;
        });
    }

    /**
     * 查询全量工具资产发现清单（包含动态 ACL 状态、所属下游启停状态与 Tool Picker 勾选状态）。
     * 对应契约：GET /api/v1/tools
     *
     * @return 工具列表
     */
    @GetMapping("/tools")
    public Mono<List<Map<String, Object>>> getTools() {
        List<RouteDefinition> routes = routeManager.getAllRoutes();
        if (routes.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return Flux.fromIterable(routes)
                .flatMap(route -> upstreamToolProvider.listTools(route)
                        .map(rawList -> {
                            List<Map<String, Object>> list = new ArrayList<>();
                            String prefix = route.getPrefix();
                            String upstreamId = route.getId();
                            boolean upstreamEnabled = route.isEnabled();
                            RouteGovernanceConfig gov = route.getGovernance();

                            for (GovernedTool raw : rawList) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                String rawName = raw.name();
                                String aggregatedName = prefix + rawName;
                                item.put("name", aggregatedName);
                                item.put("rawName", rawName);
                                item.put("upstreamId", upstreamId);
                                item.put("upstreamEnabled", upstreamEnabled);
                                item.put("description", raw.description() != null ? raw.description() : "");

                                // 计算 ACL 状态
                                String aclStatus = calculateAclStatus(gov, rawName);
                                item.put("aclStatus", aclStatus);

                                // Tool Picker 勾选状态（上游在线且 ACL 为 ALLOWED 时为 true）
                                boolean toolEnabled = upstreamEnabled && "ALLOWED".equalsIgnoreCase(aclStatus);
                                item.put("enabled", toolEnabled);

                                item.put("inputSchema", raw.inputSchema() != null ? raw.inputSchema() : Collections.emptyMap());
                                list.add(item);
                            }
                            return list;
                        })
                        .onErrorResume(e -> {
                            log.warn("控制面拉取下游工具失败: route={}, error={}", route.getId(), e.getMessage());
                            return Mono.just(Collections.emptyList());
                        })
                )
                .collectList()
                .map(nested -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (List<Map<String, Object>> sub : nested) {
                        result.addAll(sub);
                    }
                    return result;
                });
    }

    /**
     * 一键热启停指定下游 MCP Server 路由。
     * 对应契约：POST /api/v1/routes/status
     *
     * @param requestBody 入参，包含 routeId 与 enabled
     * @return 操作结果
     */
    @PostMapping("/routes/status")
    public ResponseEntity<Map<String, Object>> updateRouteStatus(@RequestBody Map<String, Object> requestBody) {
        if (requestBody == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请求体不能为空"));
        }
        Object routeIdObj = requestBody.get("routeId");
        Object enabledObj = requestBody.get("enabled");

        if (routeIdObj == null || enabledObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "routeId 和 enabled 不能为空"));
        }

        String routeId = String.valueOf(routeIdObj).trim();
        boolean enabled;
        if (enabledObj instanceof Boolean) {
            enabled = (Boolean) enabledObj;
        } else {
            enabled = Boolean.parseBoolean(String.valueOf(enabledObj));
        }

        Optional<RouteDefinition> routeOpt = routeManager.getRouteById(routeId);
        if (routeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "routeId", routeId,
                    "message", "未找到匹配的路由 ID: " + routeId
            ));
        }

        routeManager.setRouteEnabled(routeId, enabled);
        String currentStatus = enabled ? "UP" : "STOPPED";
        String message = enabled ? "Server 路由已热启用，下属工具已恢复聚合与分发" : "Server 路由已热停用，下属工具已全部下线";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("routeId", routeId);
        resp.put("enabled", enabled);
        resp.put("status", currentStatus);
        resp.put("message", message);
        return ResponseEntity.ok(resp);
    }

    /**
     * 动态下发针对工具的 ACL 访问控制策略与 Tool Picker 勾选（支持 enabled: true/false 或 action: ALLOW/DENY/RESET）。
     * 对应契约：POST /api/v1/tools/acl
     *
     * @param requestBody 入参
     * @return 操作结果
     */
    @PostMapping("/tools/acl")
    public ResponseEntity<Map<String, Object>> updateToolAcl(@RequestBody Map<String, Object> requestBody) {
        if (requestBody == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请求体不能为空"));
        }
        Object toolNameObj = requestBody.get("toolName");
        if (toolNameObj == null || String.valueOf(toolNameObj).trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "toolName 不能为空"));
        }
        String toolName = String.valueOf(toolNameObj).trim();

        // 兼容 action 与 enabled 两种入参形式
        String action = null;
        if (requestBody.containsKey("action") && requestBody.get("action") != null) {
            action = String.valueOf(requestBody.get("action")).trim();
        } else if (requestBody.containsKey("enabled") && requestBody.get("enabled") != null) {
            boolean enabledVal;
            Object eObj = requestBody.get("enabled");
            if (eObj instanceof Boolean) {
                enabledVal = (Boolean) eObj;
            } else {
                enabledVal = Boolean.parseBoolean(String.valueOf(eObj));
            }
            action = enabledVal ? "ALLOW" : "DENY";
        }

        if (action == null || action.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "action 或 enabled 必须至少指定一个"));
        }

        Optional<RouteMatchResult> matchOpt = routeManager.matchByToolName(toolName);
        if (matchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "toolName", toolName,
                    "message", "未找到匹配该工具的下游路由"
            ));
        }

        RouteMatchResult match = matchOpt.get();
        String routeId = match.getRouteDefinition().getId();
        String rawToolName = match.getTargetToolName();

        try {
            String updatedStatus = routeManager.updateToolAcl(routeId, rawToolName, action.trim());
            boolean isToolEnabled = "ALLOWED".equalsIgnoreCase(updatedStatus);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("toolName", toolName);
            resp.put("currentStatus", updatedStatus);
            resp.put("enabled", isToolEnabled);
            resp.put("message", isToolEnabled ? "工具已启用并纳入 tools/list 暴露" : "工具已禁用并从 tools/list 中静默剔除");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 获取近期脱敏审计记录快照。
     * 对应契约：GET /api/v1/audit/events
     *
     * @param limit 返回上限，默认为 50
     * @return 脱敏审计事件列表
     */
    @GetMapping("/audit/events")
    public List<Map<String, Object>> getAuditEvents(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<McpAuditEvent> events = auditRingBuffer.getRecentEvents(limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpAuditEvent event : events) {
            result.add(formatAuditEvent(event));
        }
        return result;
    }

    /**
     * 基于 SSE 的实时脱敏审计事件瀑布流长连接。
     * 对应契约：GET /api/v1/audit/stream
     *
     * @return ServerSentEvent 流式数据
     */
    @GetMapping(value = "/audit/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamAuditEvents() {
        Flux<ServerSentEvent<Map<String, Object>>> eventFlux = auditRingBuffer.asFlux()
                .map(event -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(event.eventId())
                        .event("audit")
                        .data(formatAuditEvent(event))
                        .build());

        // 每隔 15 秒发送一个心跳注释，防止网关或反向代理因为空闲断开 SSE
        Flux<ServerSentEvent<Map<String, Object>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(seq -> ServerSentEvent.<Map<String, Object>>builder()
                        .comment("ping")
                        .build());

        return Flux.merge(eventFlux, heartbeatFlux);
    }

    /**
     * 获取所有已注册的下游路由详情列表。
     * 对应契约：GET /api/v1/routes
     *
     * @return 路由列表
     */
    @GetMapping("/routes")
    public List<Map<String, Object>> getRoutes() {
        List<RouteDefinition> routes = routeManager.getAllRoutes();
        List<Map<String, Object>> list = new ArrayList<>();
        for (RouteDefinition r : routes) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("prefix", r.getPrefix());
            map.put("protocol", r.getProtocol() != null ? r.getProtocol().name() : "HTTP");
            map.put("uri", r.getUri());
            map.put("enabled", r.isEnabled());
            map.put("status", r.isEnabled() ? "UP" : "STOPPED");
            if (r.getStdioCommand() != null) {
                map.put("stdioCommand", r.getStdioCommand().getCommand());
                map.put("stdioArgs", r.getStdioCommand().getArgs());
            }
            if (r.getGovernance() != null) {
                map.put("aclMode", r.getGovernance().getAclMode());
                map.put("allowedToolsCount", r.getGovernance().getAllowedTools().size());
                map.put("deniedToolsCount", r.getGovernance().getDeniedTools().size());
            }
            list.add(map);
        }
        return list;
    }

    private String calculateAclStatus(RouteGovernanceConfig gov, String rawToolName) {
        if (gov == null) {
            return "ALLOWED";
        }
        List<String> allowed = gov.getAllowedTools();
        List<String> denied = gov.getDeniedTools();
        if (denied != null && denied.contains(rawToolName)) {
            return "DENIED";
        }
        if ("WHITELIST".equalsIgnoreCase(gov.getAclMode())) {
            return (allowed != null && allowed.contains(rawToolName)) ? "ALLOWED" : "DENIED";
        }
        return "ALLOWED";
    }

    private Map<String, Object> formatAuditEvent(McpAuditEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", event.eventId());
        map.put("toolName", event.toolName());
        map.put("callerRole", event.callerId() != null ? event.callerId() : "mcp-gateway-agent");
        map.put("status", event.status() != null ? event.status().name() : "SUCCESS");
        map.put("maskedArguments", event.arguments());
        map.put("executionTimeMs", event.durationMs());
        map.put("failureReason", event.failureReason());
        map.put("timestamp", event.timestamp().toString());
        return map;
    }
}

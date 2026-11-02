package io.github.chukui2005.mcpgovernance.gateway.adapter;

import io.github.chukui2005.mcpgov.core.runtime.McpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgov.core.runtime.audit.AuditMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.audit.DefaultMcpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.audit.McpAuditPublisher;
import io.github.chukui2005.mcpgov.core.runtime.metrics.InMemoryMcpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.McpMetricsCollector;
import io.github.chukui2005.mcpgov.core.runtime.metrics.MetricsMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.AclMode;
import io.github.chukui2005.mcpgov.core.runtime.policy.ParameterValidationMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclMcpInterceptor;
import io.github.chukui2005.mcpgov.core.runtime.policy.ToolAclPolicy;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteGovernanceConfig;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteMatchResult;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClient;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认网关执行适配器实现。
 * 负责构建 Core 的 McpInvocationContext，并在执行时装配调用 {@link McpInterceptorChain}，
 * 依次流经 Metrics、Audit、Tool ACL 与 Parameter Validation 治理流水线，杜绝安全绕过。
 * 彻底移除伪造成功的 mock 回退逻辑，真实如实暴露下游错误。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class DefaultGatewayExecutionAdapter implements GatewayExecutionAdapter {

    private static final Logger log = LoggerFactory.getLogger(DefaultGatewayExecutionAdapter.class);

    private final UpstreamClientPool clientPool;
    private final RouteManager routeManager;
    private final McpInterceptorChain interceptorChain;

    /**
     * 默认无参构造函数（使用独立默认拦截链与空连接池）。
     */
    public DefaultGatewayExecutionAdapter() {
        this(null, null, null, null, null);
    }

    /**
     * 基础双参构造函数。
     *
     * @param clientPool   出站客户端连接池
     * @param routeManager 路由管理器
     */
    public DefaultGatewayExecutionAdapter(UpstreamClientPool clientPool, RouteManager routeManager) {
        this(clientPool, routeManager, null, null, null);
    }

    /**
     * 全参构造函数，注入连接池、路由管理器及可选治理切面。
     *
     * @param clientPool       出站客户端连接池
     * @param routeManager     路由管理器
     * @param interceptorChain 治理拦截链（可选，若未指定则自动装配）
     * @param metricsCollector 遥测指标收集器（可选）
     * @param auditPublisher   审计事件发布器（可选）
     */
    @Autowired
    public DefaultGatewayExecutionAdapter(
            @Autowired(required = false) UpstreamClientPool clientPool,
            @Autowired(required = false) RouteManager routeManager,
            @Autowired(required = false) McpInterceptorChain interceptorChain,
            @Autowired(required = false) McpMetricsCollector metricsCollector,
            @Autowired(required = false) McpAuditPublisher auditPublisher) {
        this.clientPool = clientPool;
        this.routeManager = routeManager;

        if (interceptorChain != null) {
            this.interceptorChain = interceptorChain;
        } else {
            List<McpInterceptor> defaultInterceptors = new ArrayList<>();
            McpMetricsCollector mc = metricsCollector != null ? metricsCollector : new InMemoryMcpMetricsCollector();
            McpAuditPublisher ap = auditPublisher != null ? auditPublisher : new DefaultMcpAuditPublisher();

            defaultInterceptors.add(new MetricsMcpInterceptor(mc));
            defaultInterceptors.add(new AuditMcpInterceptor(ap));
            defaultInterceptors.add(new ParameterValidationMcpInterceptor(Collections.emptyList()));
            this.interceptorChain = new McpInterceptorChain(defaultInterceptors);
        }
    }

    @Override
    public McpInvocationContext buildContext(RouteMatchResult matchResult, Map<String, Object> arguments, String clientName) {
        String serverId = matchResult.getRouteDefinition().getId();
        String targetToolName = matchResult.getTargetToolName();
        Map<String, Object> safeArgs = arguments != null ? new HashMap<>(arguments) : Collections.emptyMap();
        String safeClient = clientName != null && !clientName.trim().isEmpty() ? clientName : "mcp-gateway-client";

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("serverId", serverId);
        attributes.put(AuditMcpInterceptor.ATTR_CALLER_ID, safeClient);
        attributes.put("callerApplication", safeClient);
        attributes.put("matchedPrefix", matchResult.getMatchedPrefix());

        return new McpInvocationContext(
                targetToolName,
                safeArgs,
                attributes,
                Instant.now()
        );
    }

    @Override
    public McpInvocationResult execute(McpInvocationContext context) {
        return executeReactive(context).block();
    }

    /**
     * 获取当前装配的治理拦截链。
     *
     * @return 拦截器链
     */
    public McpInterceptorChain getInterceptorChain() {
        return interceptorChain;
    }

    @Override
    public Mono<McpInvocationResult> executeReactive(McpInvocationContext context) {
        String serverId = (String) context.attributes().getOrDefault("serverId", "unknown");
        String caller = (String) context.attributes().getOrDefault(AuditMcpInterceptor.ATTR_CALLER_ID, "anonymous");

        log.info("网关开始执行治理调用: tool={}, serverId={}, caller={}", context.toolName(), serverId, caller);

        return Mono.defer(() -> {
            McpInvocationContext preHandledContext;
            ToolAclMcpInterceptor routeAclInterceptor = null;

            // 1. 若当前路由配置了特定的 Tool ACL 规则，动态进行校验
            if (routeManager != null) {
                Optional<RouteDefinition> routeOpt = routeManager.getRouteById(serverId);
                if (routeOpt.isPresent()) {
                    RouteGovernanceConfig gov = routeOpt.get().getGovernance();
                    if (gov != null && (gov.getAclMode() != null || !gov.getAllowedTools().isEmpty() || !gov.getDeniedTools().isEmpty())) {
                        AclMode aclMode = "BLACKLIST".equalsIgnoreCase(gov.getAclMode()) ? AclMode.BLACKLIST : AclMode.WHITELIST;
                        ToolAclPolicy aclPolicy = new ToolAclPolicy(true, aclMode, gov.getAllowedTools(), gov.getDeniedTools());
                        routeAclInterceptor = new ToolAclMcpInterceptor(aclPolicy);
                    }
                }
            }

            try {
                // 执行基础链的 preHandle（如 Metrics、Audit 等）
                preHandledContext = this.interceptorChain.applyPreHandle(context);
                if (routeAclInterceptor != null) {
                    preHandledContext = routeAclInterceptor.preHandle(preHandledContext);
                }
            } catch (Throwable t) {
                this.interceptorChain.applyOnError(context, t);
                return Mono.error(t);
            }

            final McpInvocationContext finalCtx = preHandledContext;
            long start = System.currentTimeMillis();

            // 2. 调度真实下游客户端
            Mono<McpInvocationResult> downstreamInvocation;
            if (clientPool != null && routeManager != null) {
                Optional<RouteDefinition> routeOpt = routeManager.getRouteById(serverId);
                if (routeOpt.isPresent()) {
                    RouteDefinition route = routeOpt.get();
                    if (isRouteExecutable(route)) {
                        try {
                            UpstreamClient client = clientPool.getClient(route);
                            downstreamInvocation = client.callTool(finalCtx.toolName(), finalCtx.arguments());
                        } catch (Exception e) {
                            long durationMs = System.currentTimeMillis() - start;
                            downstreamInvocation = Mono.just(McpInvocationResult.failure("获取下游客户端失败: " + e.getMessage(), durationMs));
                        }
                    } else {
                        long durationMs = System.currentTimeMillis() - start;
                        downstreamInvocation = Mono.just(McpInvocationResult.failure("下游路由不可执行或配置无效: " + serverId, durationMs));
                    }
                } else {
                    long durationMs = System.currentTimeMillis() - start;
                    downstreamInvocation = Mono.just(McpInvocationResult.failure("未匹配到下游路由定义: " + serverId, durationMs));
                }
            } else {
                long durationMs = System.currentTimeMillis() - start;
                downstreamInvocation = Mono.just(McpInvocationResult.failure("网关未初始化下游连接池或路由管理器", durationMs));
            }

            // 3. 执行 postHandle 与 onError 闭环
            return downstreamInvocation
                    .map(result -> {
                        return this.interceptorChain.applyPostHandle(finalCtx, result);
                    })
                    .doOnError(err -> {
                        this.interceptorChain.applyOnError(finalCtx, err);
                    });
        });
    }

    private boolean isRouteExecutable(RouteDefinition route) {
        if (route == null || route.getProtocol() == null) {
            return false;
        }
        if (route.getProtocol() == io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol.STDIO) {
            return route.getStdioCommand() != null && route.getStdioCommand().getCommand() != null
                    && !route.getStdioCommand().getCommand().trim().isEmpty();
        }
        return route.getUri() != null && !route.getUri().trim().isEmpty();
    }
}
